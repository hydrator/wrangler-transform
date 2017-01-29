/*
 * Copyright © 2016, 2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.wrangler.internal;

import co.cask.wrangler.api.DirectiveParseException;
import co.cask.wrangler.api.Directives;
import co.cask.wrangler.api.Step;
import co.cask.wrangler.steps.CharacterCut;
import co.cask.wrangler.steps.Columns;
import co.cask.wrangler.steps.Copy;
import co.cask.wrangler.steps.CsvParser;
import co.cask.wrangler.steps.Drop;
import co.cask.wrangler.steps.Expression;
import co.cask.wrangler.steps.FillNullOrEmpty;
import co.cask.wrangler.steps.FixedLengthParser;
import co.cask.wrangler.steps.Flatten;
import co.cask.wrangler.steps.FormatDate;
import co.cask.wrangler.steps.IndexSplit;
import co.cask.wrangler.steps.JsPath;
import co.cask.wrangler.steps.JsonParser;
import co.cask.wrangler.steps.Lower;
import co.cask.wrangler.steps.Mask;
import co.cask.wrangler.steps.Merge;
import co.cask.wrangler.steps.Quantization;
import co.cask.wrangler.steps.RecordConditionFilter;
import co.cask.wrangler.steps.RecordRegexFilter;
import co.cask.wrangler.steps.Rename;
import co.cask.wrangler.steps.Sed;
import co.cask.wrangler.steps.Split;
import co.cask.wrangler.steps.SplitToColumns;
import co.cask.wrangler.steps.SplitToRows;
import co.cask.wrangler.steps.TitleCase;
import co.cask.wrangler.steps.Upper;
import co.cask.wrangler.steps.XmlToJson;
import org.apache.commons.lang.StringEscapeUtils;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Parses the DSL into specification containing steps for wrangling.
 *
 * Following are some of the commands and format that {@link TextDirectives}
 * will handle.
 *
 * <ul>
 *   <li>set format csv , true</li>
 *   <li>set columns a,b,c,d,e,f,g </li>
 *   <li>rename a first</li>
 *   <li>drop b</li>
 *   <li>merge d e h |</li>
 *   <li>uppercase h</li>
 *   <li>lowercase first</li>
 *   <li>titlecase c</li>
 *   <li>indexsplit h 1 4 splitcol</li>
 * </ul>
 */
public class TextDirectives implements Directives {
  static final char TAB = '\t';

  // directives for wrangling.
  private String[] directives;

  // Mapping of specification formats.
  Map<String, String> formats = new HashMap<>();

  public TextDirectives(String[] directives) {
    this.directives = directives;

    // Add all the usages.
    formats.put("set format", "set format [csv|json] <delimiter> <skip empty lines>");
    formats.put("set column", "set column <column> <jexl-expression>");
    formats.put("set columns", "set columns <column-1, column-2, ...>");
    formats.put("rename", "rename <old> <new>");
    formats.put("drop", "drop <column>");
    formats.put("merge", "merge <first> <second> <new-column> <seperator>");
    formats.put("uppercase", "uppercase <column>");
    formats.put("lowercase", "lowercase <column>");
    formats.put("titlecase", "titlecase <column>");
    formats.put("indexsplit", "indexsplit <source> <start> <end> <destination>");
    formats.put("split", "split <source> <delimiter> <new-column-1> <new-column-2>");
    formats.put("filter-row-if-matched", "filter-row-if-matched <column> <regex>");
    formats.put("filter-row-if-true", "filter-row-if-true <condition>");
    formats.put("mask-number", "mask-number <column> <mask-pattern>");
    formats.put("mask-shuffle", "mask-shuffle <column>");
    formats.put("format-date", "format-date <column> <source-format> <destination-format>");
    formats.put("format-unix-timestamp", "format-unix-timestamp <column> <destination-format>");
    formats.put("quantize", "quantize <source-column> <destination-column> " +
      "<[range1:range2)=value>,[<range1:range2=value>]*");
    formats.put("sed", "sed <column> <expression>");
    formats.put("grep", "grep <column> <pattern>");
    formats.put("parse-as-csv", "parse-as-csv <column> <delimiter> <skip-if-empty - true or false> " +
      "<name1, name2, name3 ...>");
    formats.put("parse-as-json", "parse-as-json <column> <delete-column>");
    formats.put("parse-as-fixed-length", "parse-as-fixed-length <source> <field ranges>");
    formats.put("json-path", "json-path <source> <destination> <json path>");
    formats.put("split-to-rows","split-to-rows <column> <separator>");
    formats.put("split-to-columns","split-to-columns <column> <regex>");
    formats.put("parse-as-xml", "parse-as-xml <column>");
    formats.put("xml-path", "xml-path <source> <destination> <path>");
    formats.put("flatten", "flatten <column>[,<column>,<column>,...]");
    formats.put("parse-xml-element", "parse-xml-element <column> <delete-column>");
    formats.put("copy", "copy <source> <destination> [force]");
    formats.put("fill-null-or-empty", "fill-null-or-empty <column> <fixed-value>");
  }

  public TextDirectives(String directives) {
    this(directives.split("\n"));
  }

  /**
   * Parses the DSL to generate a sequence of steps to be executed by {@link co.cask.wrangler.api.Pipeline}.
   *
   * @return List of steps to be executed.
   * @throws ParseException
   */
  private List<Step> parse() throws DirectiveParseException {
    List<Step> steps = new ArrayList<>();

    // Split command by EOL
    int lineno = 1;

    // Iterate through each command and create necessary steps.
    for (String directive : directives) {
      StringTokenizer tokenizer = new StringTokenizer(directive, " ");
      String command = tokenizer.nextToken();

      switch (command) {
        case "set": {
          switch (tokenizer.nextToken()) {
            // set format [csv|json] <delimiter> <skip empty lines>
            case "format": {
              String format = getNextToken(tokenizer, "set format", "[csv|json]", lineno);
              if (format.equalsIgnoreCase("csv")) {
                String delimStr = getNextToken(tokenizer, "set format", "delimiter", lineno);
                char delimiter = delimStr.charAt(0);
                if (delimStr.startsWith("\\")) {
                  String unescapedStr = StringEscapeUtils.unescapeJava(delimStr);
                  if (unescapedStr == null) {
                    throw new IllegalArgumentException("Invalid delimiter for CSV Parser: " + delimStr);
                  }
                  delimiter = unescapedStr.charAt(0);
                }
                boolean ignoreEmptyLines =
                  getNextToken(tokenizer, "set format", "true|false", lineno).equalsIgnoreCase("true");
                CsvParser.Options opt = new CsvParser.Options(delimiter, ignoreEmptyLines);
                steps.add(new CsvParser(lineno, directive, opt, STARTING_COLUMN, false));
                steps.add(new Drop(lineno, directive, STARTING_COLUMN));
              } else {
                throw new DirectiveParseException(
                  String.format("Unknown format '%s' specified at line %d", format, lineno)
                );
              }
            }
            break;

            // set column <column-name> <jexl-expression>
            case "column": {
              String column = getNextToken(tokenizer, "set column", "column-name", lineno);
              String expr = getNextToken(tokenizer, "\n", "set column", "jexl-expression", lineno);
              steps.add(new Expression(lineno, directive, column, expr));
            }
            break;

            // set columns <name1, name2, ...>
            case "columns": {
              String columns = getNextToken(tokenizer, "set columns", "name1, name2, ...", lineno);
              String cols[] = columns.split(",");
              steps.add(new Columns(lineno, directive, Arrays.asList(cols)));
            }
            break;
          }
        }
        break;

        // rename <old> <new>
        case "rename": {
          String oldcol = getNextToken(tokenizer,  command, "old", lineno);
          String newcol = getNextToken(tokenizer, command, "new", lineno);
          steps.add(new Rename(lineno, directive, oldcol, newcol));
        }
        break;

        // drop <column>
        case "drop": {
          String col = getNextToken(tokenizer, command, "column", lineno);
          steps.add(new Drop(lineno, directive, col));
        }
        break;

        // merge <first> <second> <new-column> <seperator>
        case "merge": {
          String col1 = getNextToken(tokenizer, command, "first", lineno);
          String col2 = getNextToken(tokenizer, command, "second", lineno);
          String dest = getNextToken(tokenizer, command, "new-column", lineno);
          String delimiter = getNextToken(tokenizer, command, "seperator", lineno);
          steps.add(new Merge(lineno, directive, col1, col2, dest, delimiter));
        }
        break;

        // uppercase <col>
        case "uppercase": {
          String col = getNextToken(tokenizer, command, "col", lineno);
          steps.add(new Upper(lineno, directive, col));
        }
        break;

        // lowercase <col>
        case "lowercase": {
          String col = getNextToken(tokenizer, command, "col", lineno);
          steps.add(new Lower(lineno, directive, col));
        }
        break;

        // titlecase <col>
        case "titlecase": {
          String col = getNextToken(tokenizer, command, "col", lineno);
          steps.add(new TitleCase(lineno, directive, col));
        }
        break;

        // indexsplit <source> <start> <end> <destination>
        case "indexsplit": {
          String source = getNextToken(tokenizer, command, "source", lineno);
          String startStr = getNextToken(tokenizer, command, "start", lineno);
          String endStr = getNextToken(tokenizer, command, "end", lineno);
          int start = Integer.parseInt(startStr);
          int end = Integer.parseInt(endStr);
          String destination = getNextToken(tokenizer, command, "destination", lineno);
          steps.add(new IndexSplit(lineno, directive, source, start, end, destination));
        }
        break;

        // split <source-column-name> <delimiter> <new-column-1> <new-column-2>
        case "split": {
          String source = getNextToken(tokenizer, command, "source-column-name", lineno);
          String delimiter = getNextToken(tokenizer, command, "delimiter", lineno);
          String firstCol = getNextToken(tokenizer, command, "new-column-1", lineno);
          String secondCol = getNextToken(tokenizer, command, "new-column-2", lineno);
          steps.add(new Split(lineno, directive, source, delimiter, firstCol, secondCol));
        }
        break;

        // filter-row-if-matched <column> <regex>
        case "filter-row-if-matched": {
          String column = getNextToken(tokenizer, command, "column", lineno);
          String pattern = getNextToken(tokenizer, "\n", command, "regex", lineno);
          steps.add(new RecordRegexFilter(lineno, directive, column, pattern));
        }
        break;

        // filter-row-if-true  <condition>
        case "filter-row-if-true": {
          String condition = getNextToken(tokenizer, "\n", command, "condition", lineno);
          steps.add(new RecordConditionFilter(lineno, directive, condition));
        }
        break;

        // mask-number <column> <mask-pattern>
        case "mask-number": {
          String column = getNextToken(tokenizer, command, "column", lineno);
          String mask = getNextToken(tokenizer, command, "mask-pattern", lineno);
          steps.add(new Mask(lineno, directive, column, mask, Mask.MASK_NUMBER));
        }
        break;

        // mask-shuffle <column>
        case "mask-shuffle": {
          String column = getNextToken(tokenizer, command, "column", lineno);
          steps.add(new Mask(lineno, directive, column, Mask.MASK_SHUFFLE));
        }
        break;

        // format-date <column> <source-format> <destination-format>
        case "format-date": {
          String column = getNextToken(tokenizer, command, "column", 1);
          String srcDatePattern = getNextToken(tokenizer, command, "source-format", lineno);
          String dstDatePattern = getNextToken(tokenizer, "\n", command, "destination-format", lineno);
          steps.add(new FormatDate(lineno, directive, column, srcDatePattern, dstDatePattern));
        }
        break;

        // format-unix-timestamp <column> <destination-format>
        case "format-unix-timestamp": {
          String column = getNextToken(tokenizer, command, "column", lineno);
          String dstDatePattern = getNextToken(tokenizer, "\n", command, "destination-format", lineno);
          steps.add(new FormatDate(lineno, directive, column, dstDatePattern));
        }
        break;

        // quantize <source-column> <destination-column> <[range1:range2)=value>,[<range1:range2=value>]*
        case "quantize": {
          String column1 = getNextToken(tokenizer, command, "source-column", lineno);
          String column2 = getNextToken(tokenizer, command, "destination-column", lineno);
          String ranges = getNextToken(tokenizer, "\n", command, "destination-column", lineno);
          steps.add(new Quantization(lineno, directive, column1, column2, ranges));
        }
        break;

        // sed <column> <expression>
        case "sed" : {
          String column = getNextToken(tokenizer, command, "column", lineno);
          String expression = getNextToken(tokenizer, "\n", command, "expression", lineno);
          steps.add(new Sed(lineno, directive, column, expression));
        }
        break;

        // parse-as-csv <column> <delimiter> <skip-if-empty - true or false>
        case "parse-as-csv" : {
          String column = getNextToken(tokenizer, command, "column", lineno);
          String delimStr = getNextToken(tokenizer, command, "delimiter", lineno);
          char delimiter = delimStr.charAt(0);
          if (delimStr.startsWith("\\")) {
            String unescapedStr = StringEscapeUtils.unescapeJava(delimStr);
            if (unescapedStr == null) {
              throw new DirectiveParseException("Invalid delimiter for CSV Parser: " + delimStr);
            }
            delimiter = unescapedStr.charAt(0);
          }
          boolean ignoreEmptyLines =
            getNextToken(tokenizer, command, "true|false", lineno).equalsIgnoreCase("true");
          CsvParser.Options opt = new CsvParser.Options(delimiter, ignoreEmptyLines);
          steps.add(new CsvParser(lineno, directive, opt, column, false));
        }
        break;

        // parse-as-json <column> <delete-column, true|false>
        case "parse-xml-element":
        case "parse-as-json" : {
          String column = getNextToken(tokenizer, command, "column", lineno);
          String deleteCol = getNextToken(tokenizer, "\n", command, "delete-column", lineno, true);
          boolean delete = false;
          if (deleteCol != null && deleteCol.equalsIgnoreCase("true")) {
            delete = true;
          }
          steps.add(new JsonParser(lineno, directive, column, delete));
        }
        break;

        // json-path <source> <destination> <json-path>
        case "xml-path" :
        case "json-path" : {
          String src = getNextToken(tokenizer, command, "source", lineno);
          String dest = getNextToken(tokenizer, command, "dest", lineno);
          String path = getNextToken(tokenizer, "\n", command, "json-path", lineno);
          steps.add(new JsPath(lineno, directive, src, dest, path));
        }
        break;

        // parse-as-fixed-length <column> <widths> [<padding>]
        case "parse-as-fixed-length" : {
          String column = getNextToken(tokenizer, command, "column", lineno);
          String widthStr = getNextToken(tokenizer, command, "widths", lineno);
          String padding = getNextToken(tokenizer, "\n", column, "padding", lineno, true);
          if (padding == null || padding.isEmpty()) {
            padding = " "; // Add space as padding.
          }
          String[] widthsStr = widthStr.split(",");
          int[] widths = new int[widthsStr.length];
          int i = 0;
          for (String w : widthsStr) {
            try {
              widths[i] = Integer.parseInt(w);
            } catch (NumberFormatException e) {
              throw new DirectiveParseException(
                String.format("Width '%s' specified at location %d is not a number.", w, i)
              );
            }
            ++i;
          }
          steps.add(new FixedLengthParser(lineno, directive, column, widths, padding));
        }
        break;

        // split-to-rows <column> <regex>
        case "split-to-rows" : {
          String column = getNextToken(tokenizer, command, "column", lineno);
          String regex = getNextToken(tokenizer, "\n", "regex", lineno);
          steps.add(new SplitToRows(lineno, directive, column, regex));
        }
        break;

        // split-to-columns <column> <regex>
        case "split-to-columns" : {
          String column = getNextToken(tokenizer, command, "column", lineno);
          String regex = getNextToken(tokenizer, "\n", "regex", lineno);
          steps.add(new SplitToColumns(lineno, directive, column, regex));
        }
        break;

        // parse-as-xml <column>
        case "parse-as-xml" : {
          String column = getNextToken(tokenizer, command, "column", lineno);
          steps.add(new XmlToJson(lineno, directive, column));
        }
        break;

        // flatten <column>[,<column>,<column>,...]
        case "flatten" : {
          String cols = getNextToken(tokenizer, command, "columns", lineno);
          if (cols.equalsIgnoreCase("*")) {
            throw new DirectiveParseException(
              "Flatten does not support wildcard ('*') flattening. Please specify column names"
            );
          }

          String[] columns = cols.split(",");
          for (String column : columns) {
            if (column.trim().equalsIgnoreCase("*")) {
              throw new DirectiveParseException(
                "Flatten does not support wildcard ('*') flattening. Please specify column names"
              );
            }
          }
          steps.add(new Flatten(lineno, directive, columns));
        }
        break;

        // copy <source> <destination> [force]
        case "copy" : {
          String source = getNextToken(tokenizer, command, "source", lineno);
          String destination = getNextToken(tokenizer, command, "destination", lineno);
          String forceOpt = getNextToken(tokenizer, "\n", command, "force", lineno, true);

          boolean force = false;
          if (forceOpt != null && forceOpt.equalsIgnoreCase("true")) {
            force = true;
          }
          steps.add(new Copy(lineno, directive, source, destination, force));
        }
        break;

        // fill-null-or-empty <column> <fixed value>
        case "fill-null-or-empty" : {
          String column = getNextToken(tokenizer, command, "column", lineno);
          String value = getNextToken(tokenizer, command, "fixed-value", lineno);
          if (value != null && value.isEmpty()) {
            throw new DirectiveParseException(
              "Fixed value cannot be a empty string"
            );
          }
          steps.add(new FillNullOrEmpty(lineno, directive, column, value));
        }
        break;

        // cut <source> <destination> -c <range>
        // cut <source> <destination> -d <delimiter> -f <index>
        case "cut" : {
          String source = getNextToken(tokenizer, command, "source", lineno);
          String destination = getNextToken(tokenizer, command, "destination", lineno);
          String option = getNextToken(tokenizer, command, "option", lineno);
          if (!option.equalsIgnoreCase("-c") && !option.equalsIgnoreCase("-d")) {
            throw new DirectiveParseException(
              "Unknow option '" + option + "' specified. Only support character (-c) and delimited (-d) types"
            );
          }
          if (option.equalsIgnoreCase("-c")) {
            String range = getNextToken(tokenizer, command, "range", lineno);
            steps.add(new CharacterCut(lineno, directive, source, destination, range));
          }
        }
        break;

        default:
          throw new DirectiveParseException(
            String.format("Unknown directive '%s' found in the directive at line %d", command, lineno)
          );
      }
      lineno++;
    }
    return steps;
  }

  // If there are more tokens, then it proceeds with parsing, else throws exception.
  private String getNextToken(StringTokenizer tokenizer, String directive,
                          String field, int lineno) throws DirectiveParseException {
    return getNextToken(tokenizer, null, directive, field, lineno, false);
  }

  private String getNextToken(StringTokenizer tokenizer, String delimiter,
                              String directive, String field, int lineno) throws DirectiveParseException {
    return getNextToken(tokenizer, delimiter, directive, field, lineno, false);
  }

  private String getNextToken(StringTokenizer tokenizer, String delimiter,
                          String directive, String field, int lineno, boolean optional)
    throws DirectiveParseException {
    String value = null;
    if (tokenizer.hasMoreTokens()) {
      if (delimiter == null) {
        value = tokenizer.nextToken().trim();
      } else {
        value = tokenizer.nextToken(delimiter).trim();
      }
    } else {
      if (!optional) {
        String d = formats.get(directive);
        throw new DirectiveParseException(
          String.format("Missing field '%s' at line number %d for directive <%s> (usage: %s)",
                        field, lineno, directive, d)
        );
      }
    }
    return value;
  }

  /**
   * @return List of steps to executed in the order they are specified.
   * @throws ParseException throw in case of parsing exception of specification.
   */
  @Override
  public List<Step> getSteps() throws DirectiveParseException {
    return parse();
  }
}

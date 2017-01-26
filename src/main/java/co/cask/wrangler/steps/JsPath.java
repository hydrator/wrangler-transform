/*
 * Copyright © 2016 Cask Data, Inc.
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

package co.cask.wrangler.steps;

import co.cask.wrangler.api.AbstractStep;
import co.cask.wrangler.api.PipelineContext;
import co.cask.wrangler.api.Row;
import co.cask.wrangler.api.SkipRowException;
import co.cask.wrangler.api.StepException;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A Json Path Extractor Stage for parsing the {@link Row} provided based on configuration.
 */
public class JsPath extends AbstractStep {
  private String src;
  private String dest;
  private String path;

  public JsPath(int lineno, String detail, String src, String dest, String path) {
    super(lineno, detail);
    this.src = src;
    this.dest = dest;
    this.path = path;
  }

  /**
   * Parses a give column in a {@link Row} as a CSV Record.
   *
   * @param row Input {@link Row} to be wrangled by this step.
   * @param context Specifies the context of the pipeline.
   * @return New Row containing multiple columns based on CSV parsing.
   * @throws StepException In case CSV parsing generates more record.
   */
  @Override
  public Row execute(Row row, PipelineContext context) throws StepException, SkipRowException {
    Object value = row.getValue(src);
    if (value == null) {
      throw new StepException(toString() + " : Could not find field '" + src + "' in the record.");
    }

    // Detect the type of the object, convert it to String before apply JsonPath
    // expression to it.
    String v = null;
    if (value instanceof String) {
      v = (String) value;
    } else if (value instanceof JSONArray) {
      v = ((JSONArray) value).toString();
    } else if (value instanceof net.minidev.json.JSONArray) {
      v = ((net.minidev.json.JSONArray) value).toString();
    } else if (value instanceof net.minidev.json.JSONObject) {
      v = ((net.minidev.json.JSONObject) value).toString();
    } else if (value instanceof JSONObject) {
      v = ((JSONObject) value).toString();
    } else {
      throw new StepException(
        String.format("%s : Invalid value type '%s'. Should be JSONArray, JSONObject or String.", toString(),
                      value.getClass().getName())
      );
    }

    // Apply JSON path expression to it.
    Object e = Configuration.defaultConfiguration().jsonProvider().parse(v);
    Object x = JsonPath.read(e, path);

    int pos = row.find(dest);
    if (pos == -1) {
      row.add(dest, x);
    } else {
      row.setValue(pos, x);
    }

    return row;
  }

}
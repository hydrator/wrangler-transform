# Parse Json.

PARSE-AS-JSON is a directive for parsing a as json object. The directive can operate on String or JSONObject types. When the directive is applied on a String or JSONObject, the high-level keys of the json are appeneded to the original column name to create new column names. 

## Syntax 

```
  parse-as-json <column-name> [<delete-column>]
```

```column-name``` name of the column in the record that is a json object.
```delete-column``` indicates that once the value of the column has been parsed as json, the original column is deleted.

## Usage Notes

PARSE-AS-JSON directive helps you break-down a complex json into simple understandable and manageable chunks. When first applied on a json object, it breaks it down into keys and values. The value could in itself be a json object on which you can apply PARSE-AS-JSON directive again to flatten it out. 

The key names in the event object are appeneded to the column that is being applied json parsing. The column names use dot notations. To review the process of parsing let's review it with an example. Let's say you have a simple json in record with field name ```body```

```
  {
    "id" : 1,
    "name" : {
      "first" : "Root",
      "last"  : "Joltie"
    },
    "age" : 22,
    "weigth" : 184,
    "height" : 5.8
  }

```
The application of first directive

```
parse-as-json body
```

Would generate following field names and field values

| Field Name | Field Values | Field Type |
| ------------- | ------------- | ----------------- |
| **body** | ```{ ... }``` | String |
| **body.id** | 1 | Integer |
| **body.name** | ```{ "first" : "Root", "last" : "Joltie" }``` | JSONObject |
| **body.age** | 22 | Integer |
| **body.weight** | 184 | Integer |
| **body.height** | 5.8 | Double |

Applying the same directive on field ```body.name``` generates the following results

| Field Name | Field Values | Field Type |
| ------------- | ------------- | ----------------- |
| **body** | ```{ ... }``` | String |
| **body.id** | 1 | Integer |
| **body.name** | ```{ "first" : "Root", "last" : "Joltie" }``` | JSONObject |
| **body.age** | 22 | Integer |
| **body.weight** | 184 | Integer |
| **body.height** | 5.8 | Double |
| **body.name.first** | "Root" | String |
| **body.name.last** | "Joltie" | String |

## Examples

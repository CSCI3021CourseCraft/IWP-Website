package coursecraft.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

/** The tool definitions sent to the model. Names match ToolService.executeTool exactly. */
public final class ToolDefs {

    private ToolDefs() {
    }

    public static JsonArray all() {
        return JsonParser.parseString("""
        [
          {"type":"function","function":{
            "name":"get_course_notes",
            "description":"Retrieve the student's own notes (title and body) for one course.",
            "parameters":{"type":"object",
              "properties":{"course_id":{"type":"integer","description":"ID of the course"}},
              "required":["course_id"]}}},
          {"type":"function","function":{
            "name":"get_course_materials",
            "description":"Retrieve the course's assignments, modules, quizzes and files (title, due date, description). Optionally filter by type.",
            "parameters":{"type":"object",
              "properties":{
                "course_id":{"type":"integer","description":"ID of the course"},
                "type":{"type":"string","enum":["assignment","module","quiz","file"],
                        "description":"Only return items of this type. Omit to return everything."}},
              "required":["course_id"]}}},
          {"type":"function","function":{
            "name":"get_quiz_results",
            "description":"Retrieve the student's past quiz scores per topic for a course, each with a percent and a weak flag (under 70%).",
            "parameters":{"type":"object",
              "properties":{"course_id":{"type":"integer","description":"ID of the course"}},
              "required":["course_id"]}}},
          {"type":"function","function":{
            "name":"save_study_plan",
            "description":"Save a finished study plan for a course. Only call this when the student asks for a plan to be created or saved.",
            "parameters":{"type":"object",
              "properties":{
                "course_id":{"type":"integer","description":"ID of the course"},
                "plan_text":{"type":"string","description":"The complete study plan text"}},
              "required":["course_id","plan_text"]}}}
        ]
        """).getAsJsonArray();
    }
}

package org.example.client.common;

import java.util.ArrayList;
import java.util.List;

public class Response {
    private boolean success;
    private List<String> lines;

    public Response(boolean success, String line){
        this.success = success;
        this.lines = new ArrayList<>();
        this.lines.add(line);
    }

    public Response(boolean success, List<String> lines){
        this.success = success;
        this.lines = lines;
    }

    public boolean isSuccess(){
        return success;
    }

    public List<String> getLines(){
        return lines;
    }
}

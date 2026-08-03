package cn.mhook.mhook;

public class EventMessage {
    private String type;
    private String message;

    public EventMessage(String type, String message) {
        this.type = type;
        this.message = message;
    }

    @Override
    public String toString() {
        return "type="+type+"--message= "+message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
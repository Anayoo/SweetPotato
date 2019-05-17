package cn.anayoo.sweetPotato;

public class Field {

    private String name;
    private String value;
    private String type;
    private String regex;
    private boolean autoInc = false;
    private boolean allowRepeat = true;
    private boolean allowNone = true;

    public Field() {
    }

    public Field(String name, String value, String type) {
        this.name = name;
        this.value = value;
        this.type = type;
    }

    public Field(String name, String value, String type, String regex) {
        this.name = name;
        this.value = value;
        this.type = type;
        this.regex = regex;
    }

    public Field(String name, String value, String type, String regex, boolean autoInc, boolean allowRepeat, boolean allowNone) {
        this.name = name;
        this.value = value;
        this.type = type;
        this.regex = regex;
        this.autoInc = autoInc;
        this.allowRepeat = allowRepeat;
        this.allowNone = allowNone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public boolean isAutoInc() {
        return autoInc;
    }

    public void setAutoInc(boolean autoInc) {
        this.autoInc = autoInc;
    }

    public boolean isAllowRepeat() {
        return allowRepeat;
    }

    public void setAllowRepeat(boolean allowRepeat) {
        this.allowRepeat = allowRepeat;
    }

    public boolean isAllowNone() {
        return allowNone;
    }

    public void setAllowNone(boolean allowNone) {
        this.allowNone = allowNone;
    }

    @Override
    public String toString() {
        return "{" +
                "\"name\":\"" + this.name + "\", " +
                "\"value\":\"" + this.value + "\", " +
                "\"type\":\"" + this.type + "\", " +
                "\"regex\":\"" + this.regex + "\", " +
                "\"autoInc\":" + this.autoInc + ", " +
                "\"allowRepeat\":" + this.allowRepeat + ", " +
                "\"allowNone\":" + this.allowNone +
                "}";
    }
}
package cn.anayoo.sweetpotato.model;

public class Field implements Comparable<Field> {

    private String value;
    private String type;
    private String regex;
    private boolean isPrimaryKey = false;
    private boolean allowRepeat = true;
    private boolean allowNone = true;

    private String setterName;
    private String getterName;

    public Field() {
    }

    public Field(String value, String type) {
        this.value = value;
        this.type = type;
        this.setterName = "set" + value.substring(0, 1).toUpperCase() + value.substring(1);
        this.getterName = "get" + value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    public Field(String value, String type, String regex) {
        this.value = value;
        this.type = type;
        this.regex = regex;
        this.setterName = "set" + value.substring(0, 1).toUpperCase() + value.substring(1);
        this.getterName = "get" + value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    public Field(String value, String type, String regex, boolean isPrimaryKey, boolean allowRepeat, boolean allowNone) {
        this.value = value;
        this.type = type;
        this.regex = regex;
        this.isPrimaryKey = isPrimaryKey;
        this.allowRepeat = allowRepeat;
        this.allowNone = allowNone;
        this.setterName = "set" + value.substring(0, 1).toUpperCase() + value.substring(1);
        this.getterName = "get" + value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        this.setterName = "set" + value.substring(0, 1).toUpperCase() + value.substring(1);
        this.getterName = "get" + value.substring(0, 1).toUpperCase() + value.substring(1);
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

    public String getSetterName() {
        return setterName;
    }

    public String getGetterName() {
        return getterName;
    }

    @Override
    public String toString() {
        return "{" +
                "\"value\":\"" + this.value + "\", " +
                "\"type\":\"" + this.type + "\", " +
                "\"regex\":\"" + this.regex + "\", " +
                "\"allowRepeat\":" + this.allowRepeat + ", " +
                "\"allowNone\":" + this.allowNone +
                "}";
    }

    @Override
    public int compareTo(Field field) {
        if (this.isPrimaryKey) return -1;
        else return 1;
    }

    public boolean isPrimaryKey() {
        return isPrimaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        isPrimaryKey = primaryKey;
    }
}

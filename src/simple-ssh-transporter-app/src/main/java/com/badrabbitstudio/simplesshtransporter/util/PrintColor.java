package com.badrabbitstudio.simplesshtransporter.util;

public enum PrintColor {
    RESET("\u001B[0m"),
    BLACK("\u001B[30m"),
    RED("\u001B[31m"),
    GREEN("\u001B[32m"),
    YELLOW("\u001B[33m"),
    BLUE("\u001B[34m"),
    PURPLE("\u001B[35m"),
    CYAN("\u001B[36m"),
    WHITE("\u001B[37m"),
    ;

    private String _strVal;

    private PrintColor(String strVal) {
        _strVal = strVal;
    }

    public String getStrVal() {
        return _strVal;
    }

    @Override
    public String toString() {
        return _strVal;
    }


    public static ColorMsgBuilder newBuilder() {
        return new ColorMsgBuilder();
    }

    public static class ColorMsgBuilder {
        private String _str = "";

        public ColorMsgBuilder beginColor(PrintColor color) {
            _str += color.toString();
            return this;
        }

        public ColorMsgBuilder endColor() {
            _str += PrintColor.RESET.toString();
            return this;
        }

        public ColorMsgBuilder append(String msg) {
            _str += msg;
            return this;
        }

        public String build() {
            return _str;
        }
    }

}

package javax.lang.model;

/**
 * Android has no javax.lang.model. The Eclipse compiler (ECJ) reads this class when it starts,
 * so Javario ships this small stand-in. It reports Java 8 so ECJ uses the normal jar based class path.
 */
public enum SourceVersion {
    RELEASE_0, RELEASE_1, RELEASE_2, RELEASE_3, RELEASE_4, RELEASE_5, RELEASE_6, RELEASE_7, RELEASE_8,
    RELEASE_9, RELEASE_10, RELEASE_11, RELEASE_12, RELEASE_13, RELEASE_14, RELEASE_15, RELEASE_16, RELEASE_17,
    RELEASE_18, RELEASE_19, RELEASE_20, RELEASE_21;

    public static SourceVersion latest() { return RELEASE_8; }

    public static SourceVersion latestSupported() { return RELEASE_8; }

    public static boolean isIdentifier(CharSequence name) {
        String id = name.toString();
        if (id.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(id.codePointAt(0))) return false;
        for (int i = Character.charCount(id.codePointAt(0)); i < id.length(); i++) {
            if (!Character.isJavaIdentifierPart(id.codePointAt(i))) return false;
        }
        return true;
    }

    public static boolean isName(CharSequence name) {
        String id = name.toString();
        for (String s : id.split("\\.", -1)) {
            if (!isIdentifier(s) || isKeyword(s)) return false;
        }
        return true;
    }

    public static boolean isKeyword(CharSequence s) {
        switch (s.toString()) {
            case "abstract": case "continue": case "for": case "new": case "switch": case "assert": case "default":
            case "if": case "package": case "synchronized": case "boolean": case "do": case "goto": case "private":
            case "this": case "break": case "double": case "implements": case "protected": case "throw": case "byte":
            case "else": case "import": case "public": case "throws": case "case": case "enum": case "instanceof":
            case "return": case "transient": case "catch": case "extends": case "int": case "short": case "try":
            case "char": case "final": case "interface": case "static": case "void": case "class": case "finally":
            case "long": case "strictfp": case "volatile": case "const": case "float": case "native": case "super":
            case "while": case "true": case "false": case "null": case "_":
                return true;
            default:
                return false;
        }
    }
}

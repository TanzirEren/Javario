package com.tanzir.javario;

/** Lessons, challenges, quiz and tips (also used by the assistant). */
public class LearnData {
    // title, explanation, code
    public static final String[][] LESSONS = {
        {"1. Hello, World", "Every Java program starts in a class with a main method. System.out.println prints a line of text.",
            "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, Java!\");\n    }\n}\n"},
        {"2. Variables & types", "Variables store values. Common types: int (whole numbers), double (decimals), boolean (true/false), char, String (text).",
            "public class Main {\n    public static void main(String[] args) {\n        int age = 20;\n        double price = 9.99;\n        boolean ok = true;\n        char grade = 'A';\n        String name = \"Tanzir\";\n        System.out.println(name + \" is \" + age);\n        System.out.println(price + \" \" + ok + \" \" + grade);\n    }\n}\n"},
        {"3. If / else", "Use if, else if and else to choose what code runs.",
            "public class Main {\n    public static void main(String[] args) {\n        int score = 72;\n        if (score >= 80) {\n            System.out.println(\"A\");\n        } else if (score >= 60) {\n            System.out.println(\"B\");\n        } else {\n            System.out.println(\"Try again\");\n        }\n    }\n}\n"},
        {"4. Loops", "for repeats a known number of times, while repeats until a condition is false.",
            "public class Main {\n    public static void main(String[] args) {\n        for (int i = 1; i <= 5; i++) {\n            System.out.println(\"i = \" + i);\n        }\n        int n = 3;\n        while (n > 0) {\n            System.out.println(\"countdown \" + n);\n            n--;\n        }\n    }\n}\n"},
        {"5. Arrays", "An array holds many values of the same type. Index starts at 0.",
            "public class Main {\n    public static void main(String[] args) {\n        int[] nums = {5, 3, 9, 1};\n        int max = nums[0];\n        for (int x : nums) {\n            if (x > max) max = x;\n        }\n        System.out.println(\"Max = \" + max);\n        System.out.println(\"Length = \" + nums.length);\n    }\n}\n"},
        {"6. Methods", "Methods group code you can reuse. They can take parameters and return a value.",
            "public class Main {\n    static int square(int x) {\n        return x * x;\n    }\n\n    static void hello(String name) {\n        System.out.println(\"Hi \" + name);\n    }\n\n    public static void main(String[] args) {\n        hello(\"Javario\");\n        System.out.println(square(7));\n    }\n}\n"},
        {"7. Classes & objects", "A class is a blueprint. new creates an object. Fields hold data, methods hold behavior.",
            "public class Main {\n    static class Dog {\n        String name;\n        Dog(String name) { this.name = name; }\n        void bark() { System.out.println(name + \" says woof\"); }\n    }\n\n    public static void main(String[] args) {\n        Dog d = new Dog(\"Rex\");\n        d.bark();\n    }\n}\n"},
        {"8. Inheritance", "extends lets a class reuse another class. @Override replaces a parent method.",
            "public class Main {\n    static class Animal {\n        void sound() { System.out.println(\"...\"); }\n    }\n    static class Cat extends Animal {\n        @Override void sound() { System.out.println(\"Meow\"); }\n    }\n\n    public static void main(String[] args) {\n        Animal a = new Cat();\n        a.sound();\n    }\n}\n"},
        {"9. Collections", "ArrayList grows automatically. HashMap stores key/value pairs.",
            "import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        List<String> names = new ArrayList<>();\n        names.add(\"Ana\");\n        names.add(\"Bob\");\n        Map<String, Integer> ages = new HashMap<>();\n        ages.put(\"Ana\", 21);\n        ages.put(\"Bob\", 25);\n        for (String n : names) {\n            System.out.println(n + \" -> \" + ages.get(n));\n        }\n    }\n}\n"},
        {"10. Exceptions", "try / catch handles errors so your program does not crash.",
            "public class Main {\n    public static void main(String[] args) {\n        try {\n            int x = Integer.parseInt(\"abc\");\n            System.out.println(x);\n        } catch (NumberFormatException e) {\n            System.out.println(\"Not a number!\");\n        } finally {\n            System.out.println(\"Done\");\n        }\n    }\n}\n"},
        {"11. User input", "Scanner reads what the user types. In Javario type into the box at the bottom of the console.",
            "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        System.out.print(\"Your name: \");\n        String name = sc.nextLine();\n        System.out.println(\"Hello, \" + name + \"!\");\n    }\n}\n"},
        {"12. Lambdas & streams", "Lambdas are short functions. Streams process lists step by step.",
            "import java.util.*;\nimport java.util.stream.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        List<Integer> nums = Arrays.asList(1, 2, 3, 4, 5, 6);\n        List<Integer> evenSquares = nums.stream()\n            .filter(n -> n % 2 == 0)\n            .map(n -> n * n)\n            .collect(Collectors.toList());\n        System.out.println(evenSquares);\n    }\n}\n"},
    };

    // title, statement, starter code, hint, solution
    public static final String[][] CHALLENGES = {
        {"Sum 1 to N", "Read N and print the sum 1+2+...+N.", "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int n = sc.nextInt();\n        // TODO\n    }\n}\n", "Use a for loop and a running total.", "int sum = 0;\nfor (int i = 1; i <= n; i++) sum += i;\nSystem.out.println(sum);"},
        {"Even or odd", "Read a number and print even or odd.", "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int n = sc.nextInt();\n        // TODO\n    }\n}\n", "The % operator gives the remainder.", "System.out.println(n % 2 == 0 ? \"even\" : \"odd\");"},
        {"Reverse a string", "Read a word and print it reversed.", "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        String s = sc.next();\n        // TODO\n    }\n}\n", "StringBuilder has a reverse() method.", "System.out.println(new StringBuilder(s).reverse());"},
        {"Factorial", "Read n and print n! (use long).", "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int n = sc.nextInt();\n        // TODO\n    }\n}\n", "Multiply from 2 up to n.", "long f = 1;\nfor (int i = 2; i <= n; i++) f *= i;\nSystem.out.println(f);"},
        {"Fibonacci", "Print the first 10 Fibonacci numbers.", "public class Main {\n    public static void main(String[] args) {\n        // TODO\n    }\n}\n", "Keep the last two numbers in variables.", "long a = 0, b = 1;\nfor (int i = 0; i < 10; i++) {\n    System.out.println(a);\n    long t = a + b; a = b; b = t;\n}"},
        {"Prime check", "Read n and print whether it is prime.", "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int n = sc.nextInt();\n        // TODO\n    }\n}\n", "Try dividing by 2 .. sqrt(n).", "boolean prime = n > 1;\nfor (int i = 2; i * i <= n; i++) if (n % i == 0) { prime = false; break; }\nSystem.out.println(prime ? \"prime\" : \"not prime\");"},
        {"Count vowels", "Read a line and count the vowels.", "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        String s = sc.nextLine().toLowerCase();\n        // TODO\n    }\n}\n", "Loop over s.toCharArray() and check with indexOf.", "int c = 0;\nfor (char ch : s.toCharArray()) if (\"aeiou\".indexOf(ch) >= 0) c++;\nSystem.out.println(c);"},
        {"Palindrome", "Read a word and say if it is a palindrome.", "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        String s = sc.next();\n        // TODO\n    }\n}\n", "Compare the string with its reverse.", "String r = new StringBuilder(s).reverse().toString();\nSystem.out.println(s.equals(r) ? \"yes\" : \"no\");"},
        {"Multiplication table", "Print the table of 7 from 1 to 10.", "public class Main {\n    public static void main(String[] args) {\n        // TODO\n    }\n}\n", "One for loop, print 7 * i.", "for (int i = 1; i <= 10; i++) System.out.println(\"7 x \" + i + \" = \" + (7 * i));"},
        {"Bank account class", "Create class Account with deposit, withdraw and balance. Test it in main.", "public class Main {\n    // TODO: class Account\n    public static void main(String[] args) {\n    }\n}\n", "Fields + methods; do not allow a negative balance.", "static class Account {\n    private double balance;\n    void deposit(double a) { balance += a; }\n    boolean withdraw(double a) { if (a > balance) return false; balance -= a; return true; }\n    double getBalance() { return balance; }\n}"},
    };

    // question, answer keyword
    public static final String[][] QUIZ = {
        {"Which keyword creates an object? (new / make / create)", "new"},
        {"What is the index of the first element in an array?", "0"},
        {"Which type stores true or false?", "boolean"},
        {"What does the % operator return?", "remainder"},
        {"Which keyword is used to inherit from a class?", "extends"},
        {"What is the entry point method name of a Java program?", "main"},
        {"Which collection stores key/value pairs? (List / Map / Set)", "map"},
        {"Which keyword handles an exception? (try / catch / throw)", "catch"},
        {"What does 10 / 3 give in Java with ints?", "3"},
        {"Which keyword makes a variable impossible to change?", "final"},
    };

    public static final String[] TIPS = {
        "Use meaningful names: totalPrice is better than tp.",
        "Strings are compared with equals(), not ==.",
        "Array indexes start at 0 and end at length - 1.",
        "Prefer ArrayList over arrays when the size can change.",
        "Close a Scanner or other resources when you finish (try-with-resources).",
        "Small methods that do one thing are easier to test and read.",
        "Read the first compiler error first - later ones are often caused by it.",
        "Use final for values that must not change.",
        "A class name must match its file name when it is public.",
        "Use StringBuilder instead of += when joining many strings in a loop.",
    };
}

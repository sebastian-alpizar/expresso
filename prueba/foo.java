import java.util.function.*;

public class foo {

sealed interface Gender permits Male, Female {}
record Male() implements Gender {}
record Female() implements Gender {}


sealed interface Opt permits None, Some {}
record None() implements Opt {}
record Some(Object value) implements Opt {}


public static void main(String[] args) {
    Double z = 1.0;
    int x = 0;
    Function<Integer, String> goo = x_1 -> Boolean.valueOf((z + String.valueOf(x)));
    System.out.println(Integer.parseInt("666.66"));
    Function<Object, Object> add1 = x_1 -> (x + 1);
}
}

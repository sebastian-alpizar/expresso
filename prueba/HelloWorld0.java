import java.util.function.*;

public class HelloWorld0 {

public static void main(String[] args) {
    int x = 666;
    System.out.println(x);
    int y = 10;
    System.out.println(y);
    UnaryOperator<Integer> f = z -> ((int)Math.pow(z, 2) + (2 * z) + 1);
    System.out.println((f.apply(x) + f.apply(y)));
}
}

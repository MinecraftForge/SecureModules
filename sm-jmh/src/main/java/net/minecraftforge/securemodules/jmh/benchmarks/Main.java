package net.minecraftforge.securemodules.jmh.benchmarks;

// This is needed because eclipse doesn't allow for the main to be outside the module in module builds 0.o?
public class Main {
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}

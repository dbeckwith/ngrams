package net.sonaxaton.ngrams;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Created by Daniel Beckwith on 10/1/15.
 */
public class Main {

    // TODO: compare to Markov chains

    public static void main(String[] args) {
        Profiler profiler = new Profiler();

        int minN = 10;
        int maxN = 10;
        int repetitions = 1;
        int stringCount = 5;
        String sentenceDelim = "(?<![A-Z][a-z]{0,15}|\\.{2})(?<=[.!?])(?:[\"])?";

        profiler.step("Reading data");
        String text;
        try {
            URL url = Main.class.getResource("/res/hp1.txt");
            if (url == null) throw new FileNotFoundException();
            Path path = Paths.get(url.toURI());
            System.out.format("Resource path: %s%n", path);
            byte[] rawData = Files.readAllBytes(path);
            System.out.format("Data size: %,.1f KB%n", rawData.length / 1024f);
            text = new String(rawData);
        }
        catch (IOException | URISyntaxException e) {
            System.err.println("Exception reading input data: " + e);
            return;
        }
//        String text = "cool beans. this is awesome!";

        profiler.step("Parsing data");
        List<List<Character>> data = new ArrayList<>();
        Stream.of(text.split(sentenceDelim))
                .parallel()
                .map(s -> s
                                .trim()
                                .replaceAll("\\s+", " ")
                                .chars()
                                .mapToObj(c -> (char) c)
                                .collect(Collectors.toList())
                )
                .forEach(data::add);

        for (int n = minN; n <= maxN; n++) {
            profiler.step("Training " + n + "-gram");
            for (int i = 0; i < repetitions; i++) {
                NGramGenerator<Character> ng = new NGramGenerator<>(n);
                System.out.format("Number of sentences: %,d%n", data.size());
                System.out.format("Average sentence length: %,.1f%n", data.stream().mapToInt(List::size).average().getAsDouble());
                data.parallelStream().forEach(ng::train);
//                System.out.println("n-gram tree:");
//                ng.printTree();
                System.out.format("Tree size: %,d%n", ng.getTreeSize());

                if (stringCount > 0) {
                    profiler.step("Generating " + stringCount + " strings");
                    for (int j = 0; j < stringCount; j++) {
                        String generated = StreamSupport.stream(((Iterable<Character>) (ng::generateString)).spliterator(), false).map(c -> Character.toString(c)).collect(Collectors.joining());
                        System.out.format("%2d: %s%n", j + 1, generated);
                    }
                }
            }
        }

        profiler.done();
    }

}

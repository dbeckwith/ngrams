package net.sonaxaton.ngrams.test;

import net.sonaxaton.ngrams.NGramGenerator;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Created by Daniel Beckwith on 10/1/15.
 */
public class Main {

    // TODO: compare to Markov chains

    private static Profiler profiler = new Profiler();

    public static <T> void nGramProfile(int minN, int maxN, int repetitions, int stringCount, List<List<T>> data, BiConsumer<Integer, Stream<T>> output) {
        System.out.format("Number of input strings: %,d%n", data.size());
        System.out.format("Average input string length: %,.1f%n", data.stream().mapToInt(List::size).average().getAsDouble());
        for (int n = minN; n <= maxN; n++) {
            profiler.step("Training " + n + "-gram");
            for (int i = 0; i < repetitions; i++) {
                NGramGenerator<T> ng = new NGramGenerator<>(n);
                data.parallelStream().forEach(ng::train);
//                System.out.println("n-gram tree:");
//                ng.printTree();
                System.out.format("Tree size: %,d%n", ng.getTreeSize());

                if (stringCount > 0) {
                    profiler.step("Generating " + stringCount + " strings");
                    List<Stream<T>> strings = IntStream.range(0, stringCount)
                            .mapToObj(j -> StreamSupport.stream(((Iterable<T>) (ng::generateString)).spliterator(), false))
                            .collect(Collectors.toList());
                    for (int j = 0; j < stringCount; j++) {
                        output.accept(j, strings.get(j));
                    }
                }
            }
        }
    }

    public static List<List<Character>> parseSentences(Path dataPath) {
        String sentenceDelim = "(?<![A-Z][a-z]{0,15}|\\.{2})(?<=[.!?])(?:[\"])?";

        profiler.step("Reading data");
        String text = null;
        try {
            System.out.format("Resource path: %s%n", dataPath);
            byte[] rawData = Files.readAllBytes(dataPath);
            System.out.format("Data size: %,.1f KB%n", rawData.length / 1024f);
            text = new String(rawData);
        }
        catch (IOException e) {
            System.err.println("Exception reading input data: " + e);
            System.exit(1);
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

        return data;
    }

    public static void main(String[] args) {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Text files", "txt"));
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            nGramProfile(12, 12, 1, 10, parseSentences(fc.getSelectedFile().toPath()), (i, s) -> {
                String generated = s.map(c -> Character.toString(c)).collect(Collectors.joining());
                System.out.format("%2d: %s%n", i + 1, generated);
            });
            profiler.done();
        }
    }

}

package net.sonaxaton.ngrams;

import com.sun.istack.internal.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * Created by sonaxaton on 10/1/15.<br/>
 * A class that takes strings of symbols as input and then can generate random strings that resemble the input. If the
 * parameter is <code>n</code>, it does this by looking at the previous <code>n-1</code> symbols generated and using
 * these as a context. From this context it looks at the probability that given this context, certain other symbols
 * would have appeared in the input strings. Then it generates a random next symbol based on these probabilities, and
 * repeats.<br/>
 * Usage of this class involves the following steps:
 * <ol>
 * <li>Creating an instance:<br/>
 * <pre>
 *     NGramGenerator&lt;Character&gt; ng = new NGramGenerator&lt;&gt;(10);
 * </pre>
 * </li>
 * <li>Training on input data:<br/>
 * <pre>
 *     String[] sentences = {
 *         "This is a sentence.",
 *         "This is another sentence.",
 *         "Each input string gives the generator context for what symbols begin and end strings."
 *     }
 *     Stream.of(sentences)
 *         .map(s -> s.chars().mapToObj(c -> (char)c).collect(Collectors.toList()))
 *         .forEach(ng:train);
 * </pre>
 * </li>
 * <li>Generating strings:<br/>
 * <pre>
 *     List&lt;Character&gt; str;
 *     str = ng.generateString();
 *     str = ng.generateString(100);
 * </pre>
 * </li>
 * </ol>
 * The type of symbol is not limited to <code>Character</code> or <code>String</code>. Any discrete type that can be
 * organized in one-dimensional sequences could be used with this class.
 *
 * @param <T> the type of symbol used in strings in this generator
 */
public class NGramGenerator<T> {

    /**
     * A node in a tree holding symbols and their probabilities of being generated based on the input data.
     */
    private class GramNode {

        private Random random = new Random();

        Optional<T> symbol;
        int count;
        Map<Optional<T>, GramNode> subgrams;
        int subgramSum;

        GramNode(Optional<T> symbol) {
            this.symbol = symbol;
            count = 0;
            subgrams = new ConcurrentHashMap<>();
            subgramSum = 0;
        }

        /**
         * Increments the count of the given symbol in this node's subgrams. If the given symbol hasn't been counted
         * yet, it is initialized with a count of 1.
         *
         * @param symbol the symbol to count as a child of this node
         * @return the node whose count was incremented
         */
        GramNode countSymbol(Optional<T> symbol) {
            // get the subgram from this node corresponding to the given symbol
            GramNode subgram = subgrams.compute(symbol, (s, g) -> {
                if (g == null) {
                    // if it doesn't exist yet, create a new one
                    treeSize++;
                    return new GramNode(symbol);
                }
                return g;
            });
            // increment its count
            subgram.count++;
            // also keep track of this node's childrens' count sum
            subgramSum++;
            return subgram;
        }

        // TODO: allow general picking function

        /**
         * Randomly picks a node from this node's subgrams, using their counts as weights.
         *
         * @return the randomly selected node
         */
        GramNode pick() {
            // choose a random number
            int r = random.nextInt(subgramSum);
            // x is the progress along the list of weighted symbols
            int x = 0;
            for (GramNode node : subgrams.values()) {
                // increment by current node's weight
                // if the weight is greater than the random number, pick this node
                if ((x += node.count) >= r) return node;
            }
            throw new IllegalStateException("Weights were probably not calculated");
        }

        /**
         * Outputs a tree representation of this node and its subnodes to {@link System#out}.
         */
        void printTree() {
            printTree(0);
        }

        private void printTree(int depth) {
            for (int i = 0; i < depth; i++) System.out.print('\t');
            System.out.println(this);
            subgrams.values().forEach(node -> node.printTree(depth + 1));
        }

        @Override
        public String toString() {
            return String.format("[%s]=%d (subsum=%d)", symbol.map(T::toString).orElse(""), count, subgramSum);
        }
    }

    /**
     * A class representing a symbol's context in a string, implemented as a fixed-size list of symbols. The list is of
     * fixed size because it starts initially as being filled with blank symbols, then when new symbols are added, the
     * oldest symbols are discarded.
     *
     * @param <T> the type of symbol
     */
    public static class SymbolContext<T> implements Iterable<Optional<T>> {

        private final Object[] symbols;
        private int pos;

        private SymbolContext(Object[] symbols, int pos) {
            this.symbols = symbols;
            this.pos = pos;
        }

        /**
         * Creates a new symbol context of the given size, initially filled with all blanks.
         *
         * @param size the size of this context
         */
        public SymbolContext(int size) {
            symbols = new Object[size];
            pos = 0;
            for (int i = 0; i < size; i++) {
                symbols[i] = Optional.<T>empty();
            }
        }

        /**
         * Gets an element of this context at the given index.
         *
         * @param i the index to find an element at
         * @return the symbol at the given index
         */
        public Optional<T> get(int i) {
            if (i < 0 || i >= symbols.length) throw new IndexOutOfBoundsException();
            @SuppressWarnings("unchecked")
            Optional<T> symbol = (Optional<T>) symbols[(i + pos) % symbols.length];
            return symbol;
        }

        /**
         * Adds a symbol to the end this context, shifting previous values backwards. The first element is discarded.
         * A null value indicates a blank symbol.
         *
         * @param symbol the symbol to add, optionally <code>null</code>
         */
        public void add(T symbol) {
            add(Optional.ofNullable(symbol));
        }

        /**
         * Adds a symbol to the end this context, shifting previous values backwards. The first element is discarded.
         *
         * @param symbol the symbol to add
         */
        public void add(@NotNull Optional<T> symbol) {
            Objects.requireNonNull(symbol, "Symbol should not be null. To indicate a blank, use Optional.empty()");
            if (symbols.length > 0) {
                symbols[pos] = symbol;
                pos = (pos + 1) % symbols.length;
            }
        }

        /**
         * Gets the size of this context.
         *
         * @return the size of this context
         */
        public int size() {
            return symbols.length;
        }

        @Override
        public Iterator<Optional<T>> iterator() {
            return new Iterator<Optional<T>>() {

                private int i = 0;

                @Override
                public boolean hasNext() {
                    return i < symbols.length;
                }

                @Override
                public Optional<T> next() {
                    if (hasNext())
                        return get(i++);
                    throw new NoSuchElementException();
                }
            };
        }

        /**
         * Makes a shallow copy of this context.
         *
         * @return a shallow copy of this context
         */
        public SymbolContext<T> copy() {
            return new SymbolContext<>(symbols.clone(), pos);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            for (int i = 0; i < symbols.length; i++) {
                sb.append(get(i));
                if (i < symbols.length - 1) sb.append(", ");
            }
            sb.append("}");
            return sb.toString();
        }

    }

    /**
     * An iterator that produces strings of symbols. The strings can be of unbounded length, or have a maximum length.
     */
    private class NGStringIterator implements Iterator<T> {

        private final SymbolContext<T> context;
        private long count;
        private final long maxLength;
        private final boolean noLimit;
        private GramNode nextNode;

        /**
         * Creates a new iterator that will produce strings from this n-gram.
         *
         * @param maxLength   the limit on the length of the string generated
         * @param seedContext the starting symbol context for the string
         */
        private NGStringIterator(long maxLength, SymbolContext<T> seedContext) {
            context = seedContext.copy();
            count = 0L;
            this.maxLength = maxLength;
            noLimit = maxLength < 0;

            // pre-generate next node
            genNextNode();
        }

        /**
         * Picks the next symbol in the string based on the current context, then advances the context.
         */
        private void genNextNode() {
            // go down tree with context to find subgrams corresponding to current context
            GramNode currNode = root;
            for (Optional<T> symbol : context) {
                currNode = currNode.subgrams.get(symbol);
                if (currNode == null) {
                    throw new IllegalStateException("Context not found in ngram tree: " + context);
                }
            }
            // get random final leaf node
            nextNode = currNode.pick();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasNext() {
            // the string is ended either when the max length is reached or the next symbol selected was a blank
            return (noLimit || count < maxLength) && nextNode.symbol.isPresent();
        }

        @Override
        public T next() {
            if (hasNext()) {
                // record node used, advance context
                context.add(nextNode.symbol);
                if (!noLimit) count++;

                T symbol = nextNode.symbol.get();
                genNextNode(); // pre-generate next node
                return symbol;
            }
            throw new NoSuchElementException();
        }
    }

    private final int n;
    private final GramNode root;
    private long treeSize;

    /**
     * Creates a new n-gram generator with n given.
     *
     * @param n the number of grams to use to generate strings
     */
    public NGramGenerator(int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be positive");
        this.n = n;
        root = new GramNode(Optional.<T>empty());
        root.count = 1;
        treeSize = 1L;
    }

    /**
     * Adds to this generator's symbol probability tree based on the given input string.
     *
     * @param data the input string
     */
    public void train(List<T> data) {
        // create a stream of contexts with endings from index 0 to index size (inclusive)
        // so the first context is n-1 blanks with the first symbol of the string at the end
        // and the last context has the last n-1 symbols of the string with a blank at the end
        IntStream.rangeClosed(-(n - 1), data.size() - (n - 1))
                .mapToObj(i -> {
                    // build the context starting at index i, using nulls wherever it goes outside the string
                    @SuppressWarnings("unchecked")
                    T[] context = (T[]) new Object[n];
                    for (int j = 0; j < context.length; j++) {
                        context[j] = j + i < 0 || j + i >= data.size() ? null : data.get(j + i);
                    }
//            if (i == -(n - 1) && Objects.equals(data.get(context.length - 1 + i), '.'))
//                System.out.println("period: " + Arrays.toString(context));
                    return context;
                })
                        // now in parallel, count each context in the tree
                .parallel()
                .forEach(context -> {
                    // starting at the root node, record the full context in the tree
                    GramNode node = root;
                    for (T symbol : context)
                        node = node.countSymbol(Optional.ofNullable(symbol));
                });
    }

    /**
     * Prints a tree representation of the currently trained data to {@link System#out}.
     */
    public void printTree() {
        root.printTree();
    }

    /**
     * Gets the total number of nodes in the tree.
     *
     * @return the total number of nodes in the tree
     */
    public long getTreeSize() {
        return treeSize;
    }

    /**
     * Generate a string from this n-gram tree with no length limit.
     *
     * @return a random string generated from this n-gram tree
     */
    public Iterator<T> generateString() {
        return generateString(-1L);
    }

    /**
     * Generate a string from this n-gram tree with a length limit.
     *
     * @param maxLength the maximum length string to be generated
     * @return a random string generated from this n-gram tree
     */
    public Iterator<T> generateString(long maxLength) {
        return generateString(maxLength, new SymbolContext<>(n - 1));
    }

    private Iterator<T> generateString(SymbolContext<T> seedContext) {
        return generateString(-1L, seedContext);
    }

    private Iterator<T> generateString(long maxLength, SymbolContext<T> seedContext) {
        if (root.subgrams.isEmpty())
            throw new IllegalStateException("generator must be trained before it can generate strings");
        return new NGStringIterator(maxLength, seedContext);
    }

}

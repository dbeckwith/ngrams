package net.sonaxaton.ngrams.test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Daniel Beckwith on 9/29/15.
 */
public class Profiler {

    private static class ProfileStep {

        private final String name;
        private final long time;

        public ProfileStep(String name, long time) {
            this.name = name;
            this.time = time;
        }

        public String getName() {
            return name;
        }

        public long getTime() {
            return time;
        }
    }

    private long timer;
    private String lastMsg;
    private final List<ProfileStep> steps;

    public Profiler() {
        timer = 0;
        lastMsg = null;
        steps = new ArrayList<>();
    }

    public void reset() {
        timer = 0;
        lastMsg = null;
        steps.clear();
    }

    public void step(String msg) {
        if (lastMsg == null) {
            timer = System.nanoTime();
            lastMsg = msg;
        }
        else {
            long time = -timer + (timer = System.nanoTime());
            steps.add(new ProfileStep(lastMsg, time));
            lastMsg = msg;
        }
        System.out.println(msg + "...");
    }

    public void done() {
        if (lastMsg == null) {
            System.out.println("No profiling steps");
            return;
        }

        long time = System.nanoTime() - timer;
        steps.add(new ProfileStep(lastMsg, time));

        int maxNameLen = steps.stream().map(ProfileStep::getName).mapToInt(String::length).max().getAsInt();
        System.out.println("\nProfile results:");
        steps.forEach(step -> System.out.format("%" + maxNameLen + "s : %9.3fs%n", step.getName(), step.getTime() / 1e9));

        reset();
    }

}

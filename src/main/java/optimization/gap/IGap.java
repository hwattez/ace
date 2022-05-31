package optimization.gap;

public interface IGap {
    public boolean isContinuous();

    public void afterRun();

    public long nextGap();

    public boolean hasBeenAlwaysSafe();

}


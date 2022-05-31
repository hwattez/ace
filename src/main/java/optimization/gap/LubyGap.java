package optimization.gap;

import optimization.Optimizer;
import problem.Problem;

public class LubyGap extends GapStrategy {
    private long un = 1;
    private long vn = 1;

    public LubyGap(Optimizer optimizer, boolean minimization, Problem problem) {
        super(optimizer, minimization, problem);
    }

    /**
     * Computes and return the next value of the luby sequence. That method has
     * a side effect of the value returned by luby(). luby()!=nextLuby() but
     * nextLuby()==luby().
     *
     * @return the new current value of the luby sequence.
     */
    private long nextLuby() {
        if ((this.un & -this.un) == this.vn) {
            this.un = this.un + 1;
            this.vn = 1;
        } else {
            this.vn = this.vn << 1;
        }
        return this.vn;
    }
    @Override
    protected long reinit() {
        this.un = 1;
        this.vn=1;
        return this.gap = this.vn;
    }

    @Override
    protected long internalNextGap() {
        return this.gap = this.nextLuby();
    }

}

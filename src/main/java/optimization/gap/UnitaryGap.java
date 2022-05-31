package optimization.gap;

import optimization.Optimizer;
import problem.Problem;

public class UnitaryGap extends GapStrategy {

    public UnitaryGap(Optimizer optimizer, boolean minimization, Problem problem) {
        super(optimizer, minimization, problem);
    }

    @Override
    public boolean isContinuous() {
        return true;
    }

    @Override
    protected long reinit() {
        return 1;
    }

    @Override
    protected long internalNextGap() {
        // TODO Auto-generated method stub
        return 1;

    }

}



package interfaces;

public interface Tags {

	interface TagExperimental {
	}

	/**
	 * Useful to tag constraints that can produce full filtering at each call (not only around the specified event variable)
	 */
	interface TagFilteringCompleteAtEachCall {
	}

	/**
	 * Useful to tag constraints that guarantee enforcing GAC.
	 */
	interface TagGACGuaranteed {
	}

	/**
	 * Useful to tag constraints that does not guarantee enforcing GAC.
	 */
	interface TagGACUnguaranteed {
	}

	interface TagMaximize {
	}

	interface TagNegative {
	}

	interface TagPositive {
	}

	interface TagShort {
	}

	/**
	 * Useful to tag constraints that are completely symmetric
	 */
	interface TagSymmetric {
	}

	/**
	 * Useful to tag constraints that have no symmetry at all.
	 */
	interface TagUnsymmetric {
	}

}

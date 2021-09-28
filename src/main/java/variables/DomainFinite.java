/*
 * This file is part of the constraint solver ACE (AbsCon Essence). 
 *
 * Copyright (c) 2021. All rights reserved.
 * Christophe Lecoutre, CRIL, Univ. Artois and CNRS. 
 * 
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */

package variables;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.xcsp.common.Constants;
import org.xcsp.common.Range;
import org.xcsp.common.Utilities;

import propagation.Propagation;
import sets.SetLinkedFinite.LinkedSetOrderedWithBits;
import utility.Kit;

/**
 * A finite domain for a variable (from a constraint network), composed of a finite set of integers. Such a domain is
 * defined from a range or an array; see the two intern subclasses.
 * 
 * @author Christophe Lecoutre
 */
public abstract class DomainFinite extends LinkedSetOrderedWithBits implements Domain {

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof DomainFinite))
			return false;
		DomainFinite d = (DomainFinite) obj;
		if (this.size != d.size)
			return false;
		for (int a = first; a != -1; a = next(a))
			if (!d.contains(a))
				return false;
		return true;
	}

	private Variable var;

	private Integer typeIdentifier;

	private Propagation propagation;

	private Boolean indexesMatchValues;

	@Override
	public final Variable var() {
		return var;
	}

	/**
	 * Computes and returns the type identifier of the domain
	 * 
	 * @return the type identifier of the domain
	 */
	protected abstract int computeTypeIdentifier();

	@Override
	public final int typeIdentifier() {
		return typeIdentifier != null ? typeIdentifier : (typeIdentifier = computeTypeIdentifier());
	}

	@Override
	public final Propagation propagation() {
		return propagation;
	}

	@Override
	public final void setPropagation(Propagation propagation) {
		this.propagation = propagation;
	}

	@Override
	public final boolean indexesMatchValues() {
		return indexesMatchValues != null ? indexesMatchValues : (indexesMatchValues = IntStream.range(0, initSize()).noneMatch(a -> a != toVal(a)));
	}

	/**
	 * Builds a finite domain of the specified initial size for the specified variable
	 * 
	 * @param var
	 *            the variable to which the domain is associated
	 * @param initSize
	 *            the initial size of the domain
	 */
	public DomainFinite(Variable var, int initSize) {
		super(initSize);
		this.var = var;
		Kit.control(0 < initSize && initSize <= Constants.MAX_SAFE_INT);
	}

	@Override
	public String toString() {
		return "dom(" + var() + ")";
	}

	/**
	 * This class gives the description of a domain composed of a list of integers included between two (integer)
	 * bounds.
	 */
	public final static class DomainRange extends DomainFinite {

		/**
		 * The minimal value of the domain
		 */
		public final int min;

		/**
		 * The maximal value of the domain (included)
		 */
		public final int max;

		@Override
		protected int computeTypeIdentifier() {
			return Domain.typeIdentifierForRange(min, max);
		}

		public DomainRange(Variable var, int min, int max) {
			super(var, max - min + 1);
			this.min = min;
			this.max = max;
			Kit.control(Constants.MIN_SAFE_INT <= min && min <= max && max <= Constants.MAX_SAFE_INT, () -> "badly formed domain for variable " + var);
		}

		@Override
		public int toIdx(int v) {
			return v < min || v > max ? -1 : v - min;
		}

		@Override
		public int toVal(int a) {
			return (a + min) <= max ? a + min : -1;
		}

		@Override
		public Object allValues() {
			return new Range(min, max + 1);
		}
	}

	/**
	 * This class describes domains composed of a list of integers that are not necessarily contiguous. Be careful: the
	 * values are sorted.
	 */
	public static class DomainValues extends DomainFinite {

		private static final int DIRECT_INDEXING_LIMIT = 1000; // TODO hard coding

		/**
		 * The values of the domain
		 */
		public final int[] values;

		/**
		 * The indexes of values (possibly null)
		 */
		public final int[] indexes;

		private int firstValue, lastValue;

		@Override
		protected int computeTypeIdentifier() {
			return Domain.typeIdentifierFor(values);
		}

		public DomainValues(Variable var, int... values) {
			super(var, values.length);
			assert Kit.isStrictlyIncreasing(values);
			assert this instanceof DomainSymbols || IntStream.range(0, values.length - 1).anyMatch(i -> values[i + 1] != values[i] + 1);
			Kit.control(Constants.MIN_SAFE_INT <= values[0] && values[values.length - 1] <= Constants.MAX_SAFE_INT);
			this.values = values;
			this.firstValue = values[0];
			this.lastValue = values[values.length - 1];
			if (lastValue - firstValue < DIRECT_INDEXING_LIMIT) {
				this.indexes = Kit.repeat(-1, lastValue - firstValue + 1);
				for (int i = 0; i < values.length; i++)
					indexes[values[i] - firstValue] = i;
			} else
				this.indexes = null;
		}

		@Override
		public int toIdx(int v) {
			if (indexes != null)
				return v < firstValue || v > lastValue ? -1 : indexes[v - firstValue];
			return Arrays.binarySearch(values, v); // TODO should we prefer using a map ? it seems so, but to be tested.
		}

		@Override
		public final int toVal(int a) {
			return values[a];
		}

		@Override
		public Object allValues() {
			return values;
		}
	}

	/**
	 * This class describes domains composed of a list of symbols, where each such symbol is associated with a value
	 * (just introduced to handle symbols in the solver).
	 */
	public final static class DomainSymbols extends DomainValues {

		public final String[] symbols;

		@Override
		protected int computeTypeIdentifier() {
			return Domain.typeIdentifierForSymbols(values);
		}

		public DomainSymbols(Variable var, int[] vals, String[] symbols) {
			super(var, vals);
			Kit.control(symbols != null && symbols.length > 0 && vals.length == symbols.length, () -> "badly formed set of symbols for variable " + var);
			// below we sort the array of symbols according to the way the array of values have been sorted (in the
			// super-constructor)
			this.symbols = Arrays.stream(Kit.buildMapping(this.values, vals)).mapToObj(i -> symbols[i]).toArray(String[]::new);
		}

		@Override
		public String prettyValueOf(int a) {
			return symbols[a];
		}

		@Override
		public String stringOfCurrentValues() {
			StringBuilder sb = new StringBuilder();
			for (int a = first(); a != -1; a = next(a))
				sb.append(a != first() ? ' ' : "").append(symbols[a]);
			return sb.toString();
		}

		public int toIdx(String v) {
			return Utilities.indexOf(v, symbols); // TODO using a map instead ?
		}
	}

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.runtime.utils;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.table.api.dataview.ListView;
import org.apache.flink.table.api.dataview.MapView;
import org.apache.flink.table.functions.AggregateFunction;

import java.util.Iterator;

/**
 * Test aggregator functions.
 */
public class JavaUserDefinedAggFunctions {
	/**
	 * Accumulator for test requiresOver.
 	 */
	public static class Accumulator0 extends Tuple2<Long, Integer>{}

	/**
	 * Test for requiresOver.
	 */
	public static class OverAgg0 extends AggregateFunction<Long, Accumulator0> {
		@Override
		public Accumulator0 createAccumulator() {
			return new Accumulator0();
		}

		@Override
		public Long getValue(Accumulator0 accumulator) {
			return 1L;
		}

		//Overloaded accumulate method
		public void accumulate(Accumulator0 accumulator, long iValue, int iWeight) {
		}

		@Override
		public boolean requiresOver() {
			return true;
		}
	}

	/**
	 * Accumulator for WeightedAvg.
	 */
	public static class WeightedAvgAccum {
		public long sum = 0;
		public int count = 0;
	}

	/**
	 * Base class for WeightedAvg.
	 */
	public static class WeightedAvg extends AggregateFunction<Long, WeightedAvgAccum> {
		@Override
		public WeightedAvgAccum createAccumulator() {
			return new WeightedAvgAccum();
		}

		@Override
		public Long getValue(WeightedAvgAccum accumulator) {
			if (accumulator.count == 0) {
				return null;
			} else {
				return accumulator.sum / accumulator.count;
			}
		}

		// overloaded accumulate method
		public void accumulate(WeightedAvgAccum accumulator, long iValue, int iWeight) {
			accumulator.sum += iValue * iWeight;
			accumulator.count += iWeight;
		}

		//Overloaded accumulate method
		public void accumulate(WeightedAvgAccum accumulator, int iValue, int iWeight) {
			accumulator.sum += iValue * iWeight;
			accumulator.count += iWeight;
		}
	}

	/**
	 * A WeightedAvg class with merge method.
	 */
	public static class WeightedAvgWithMerge extends WeightedAvg {
		public void merge(WeightedAvgAccum acc, Iterable<WeightedAvgAccum> it) {
			Iterator<WeightedAvgAccum> iter = it.iterator();
			while (iter.hasNext()) {
				WeightedAvgAccum a = iter.next();
				acc.count += a.count;
				acc.sum += a.sum;
			}
		}
	}

	/**
	 * A WeightedAvg class with merge and reset method.
	 */
	public static class WeightedAvgWithMergeAndReset extends WeightedAvgWithMerge {
		public void resetAccumulator(WeightedAvgAccum acc) {
			acc.count = 0;
			acc.sum = 0L;
		}
	}

	/**
	 * A WeightedAvg class with retract method.
	 */
	public static class WeightedAvgWithRetract extends WeightedAvg {
		//Overloaded retract method
		public void retract(WeightedAvgAccum accumulator, long iValue, int iWeight) {
			accumulator.sum -= iValue * iWeight;
			accumulator.count -= iWeight;
		}

		//Overloaded retract method
		public void retract(WeightedAvgAccum accumulator, int iValue, int iWeight) {
			accumulator.sum -= iValue * iWeight;
			accumulator.count -= iWeight;
		}
	}

	/**
	 * CountDistinct accumulator.
	 */
	public static class CountDistinctAccum {
		public MapView<String, Integer> map;
		public long count;
	}

	/**
	 * CountDistinct aggregate.
	 */
	public static class CountDistinct extends AggregateFunction<Long, CountDistinctAccum> {

		@Override
		public CountDistinctAccum createAccumulator() {
			CountDistinctAccum accum = new CountDistinctAccum();
			accum.map = new MapView<>(Types.STRING, Types.INT);
			accum.count = 0L;
			return accum;
		}

		//Overloaded accumulate method
		public void accumulate(CountDistinctAccum accumulator, String id) {
			try {
				Integer cnt = accumulator.map.get(id);
				if (cnt != null) {
					cnt += 1;
					accumulator.map.put(id, cnt);
				} else {
					accumulator.map.put(id, 1);
					accumulator.count += 1;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		//Overloaded accumulate method
		public void accumulate(CountDistinctAccum accumulator, long id) {
			try {
				Integer cnt = accumulator.map.get(String.valueOf(id));
				if (cnt != null) {
					cnt += 1;
					accumulator.map.put(String.valueOf(id), cnt);
				} else {
					accumulator.map.put(String.valueOf(id), 1);
					accumulator.count += 1;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public Long getValue(CountDistinctAccum accumulator) {
			return accumulator.count;
		}
	}

	/**
	 * CountDistinct aggregate with merge.
	 */
	public static class CountDistinctWithMerge extends CountDistinct {

		//Overloaded merge method
		public void merge(CountDistinctAccum acc, Iterable<CountDistinctAccum> it) {
			Iterator<CountDistinctAccum> iter = it.iterator();
			while (iter.hasNext()) {
				CountDistinctAccum mergeAcc = iter.next();
				acc.count += mergeAcc.count;

				try {
					Iterator<String> itrMap = mergeAcc.map.keys().iterator();
					while (itrMap.hasNext()) {
						String key = itrMap.next();
						Integer cnt = mergeAcc.map.get(key);
						if (acc.map.contains(key)) {
							acc.map.put(key, acc.map.get(key) + cnt);
						} else {
							acc.map.put(key, cnt);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * CountDistinct aggregate with merge and reset.
	 */
	public static class CountDistinctWithMergeAndReset extends CountDistinctWithMerge {

		//Overloaded retract method
		public void resetAccumulator(CountDistinctAccum acc) {
			acc.map.clear();
			acc.count = 0;
		}
	}

	/**
	 * CountDistinct aggregate with retract.
	 */
	public static class CountDistinctWithRetractAndReset extends CountDistinct {

		//Overloaded retract method
		public void retract(CountDistinctAccum accumulator, long id) {
			try {
				Integer cnt = accumulator.map.get(String.valueOf(id));
				if (cnt != null) {
					cnt -= 1;
					if (cnt <= 0) {
						accumulator.map.remove(String.valueOf(id));
						accumulator.count -= 1;
					} else {
						accumulator.map.put(String.valueOf(id), cnt);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		//Overloaded retract method
		public void resetAccumulator(CountDistinctAccum acc) {
			acc.map.clear();
			acc.count = 0;
		}
	}

	/**
	 * Accumulator for test DataView.
	 */
	public static class DataViewTestAccum {
		public MapView<String, Integer> map;
		public MapView<String, Integer> map2; // for test not initialized
		public long count;
		private ListView<Long> list = new ListView<>(Types.LONG);

		public ListView<Long> getList() {
			return list;
		}

		public void setList(ListView<Long> list) {
			this.list = list;
		}
	}

	public static boolean isCloseCalled = false;

	/**
	 * Aggregate for test DataView.
	 */
	public static class DataViewTestAgg extends AggregateFunction<Long, DataViewTestAccum> {
		@Override
		public DataViewTestAccum createAccumulator() {
			DataViewTestAccum accum = new DataViewTestAccum();
			accum.map = new MapView<>(Types.STRING, Types.INT);
			accum.count = 0L;
			return accum;
		}

		// Overloaded accumulate method
		public void accumulate(DataViewTestAccum accumulator, String a, long b) {
			try {
				if (!accumulator.map.contains(a)) {
					accumulator.map.put(a, 1);
					accumulator.count++;
				}

				accumulator.list.add(b);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public Long getValue(DataViewTestAccum accumulator) {
			long sum = accumulator.count;
			try {
				for (Long value : accumulator.list.get()) {
					sum += value;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return sum;
		}

		@Override
		public void close() {
			isCloseCalled = true;
		}
	}
}

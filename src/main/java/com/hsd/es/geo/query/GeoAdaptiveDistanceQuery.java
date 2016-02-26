package com.hsd.es.geo.query;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.DocIdSetBuilder;
import org.apache.lucene.util.GeoUtils;
import org.apache.lucene.util.ToStringUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.DistanceUnit;

import com.hsd.es.geo.utils.GPSUtils;
import com.hsd.es.geo.utils.pojo.Curve;

import java.io.IOException;
import java.util.Set;

/**
 * Copied from {@link MatchAllDocsQuery}, calculate score for all docs
 */
public class GeoAdaptiveDistanceQuery extends Query {

	private String fieldName;
	private GeoPoint pointParam;
	private Double distanceParam;
	private Curve curveParam;
	private float boost;

	public GeoAdaptiveDistanceQuery(String fieldName, GeoPoint point, Double distance, Curve curve, float boost) {
		this.fieldName = fieldName;
		this.pointParam = point;
		this.distanceParam = distance;
		this.curveParam = curve;
		this.boost = boost;
		setBoost(boost);
	}

	private class GeoAdaptiveDistanceScorer extends Scorer {
		private int doc = -1;
		private final int maxDoc;
		private final Bits liveDocs;
		// private BinaryDocValues binaryDocValues;

		private final IndexReader reader;
		// private final float boost;
		private SortedNumericDocValues sortedNumericDocValues;

		GeoAdaptiveDistanceScorer(IndexReader reader, Bits liveDocs, Weight weight) {
			super(weight);
			// super(w, fieldName, lireFeature, reader,
			// ImageQuery.this.getBoost());
			this.reader = reader;
			this.liveDocs = liveDocs;
			maxDoc = reader.maxDoc();
		}

		@Override
		public int docID() {
			return doc;
		}

		@Override
		public int nextDoc() throws IOException {
			doc++;
			while (liveDocs != null && doc < maxDoc && !liveDocs.get(doc)) {
				doc++;
			}
			if (doc == maxDoc) {
				doc = NO_MORE_DOCS;
			}
			return doc;
		}

		@Override
		public int advance(int target) throws IOException {
			doc = target - 1;
			return nextDoc();
		}

		@Override
		public long cost() {
			return maxDoc;
		}

		@Override
		public float score() throws IOException {
			assert docID() != NO_MORE_DOCS;

			if (sortedNumericDocValues == null) {
				LeafReader atomicReader = (LeafReader) reader;
				sortedNumericDocValues = atomicReader.getSortedNumericDocValues(fieldName);
			}

			try {
				sortedNumericDocValues.setDocument(docID());
				double lon = 0, lat = 0;
				final long hash = sortedNumericDocValues.valueAt(docID());
				lon = GeoUtils.mortonUnhashLon(hash);
				lat = GeoUtils.mortonUnhashLat(hash);
				double distance = GPSUtils.getDistance(pointParam.getLat(), pointParam.getLon(), lat, lon);
				float score = 0f;
//				score = (float) (1.0 / (Math.abs(lon + lat) + 1));
				Double distCoef = Math.abs(distance/distanceParam);
				switch (curveParam) {
				case LINEAR:
					score = (float) (1 - distCoef);
					if (score < 0.1) {
						score = (float) (0.1/distCoef);
					}
					break;
				case COSINUS:
					if (distCoef < 1) {
						score = (float) Math.cos(Math.PI/2.0*distCoef);
					} else {
						score = 0f;
					}
					if (score < 0.1) { // distCoef = 0.935
						score = (float) (0.0935/distCoef);
					}
					break;
				case X2:
					if (distCoef < 1) {
						score = (float) ((distCoef - 1)*(distCoef - 1));
					} else {
						score = 0f;
					}
					if (score < 0.04) { // 0.2*0.2, distCoef = 0.8
						score = (float) (0.032/distCoef);
					}
					break;
				}
				if (boost != 0) {
					score = score * boost;
				}
				return score;
			} catch (Exception e) {
				throw new ElasticsearchException("Failed to calculate score", e);
			}
		}

		@Override
		public int freq() {
			return 1;
		}
	}

	private class GeoAdaptiveDistanceWeight extends Weight {

		protected GeoAdaptiveDistanceWeight(Query query) {
			super(query);
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			// TODO Auto-generated method stub
		}

		@Override
		public Explanation explain(LeafReaderContext context, int doc) throws IOException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public float getValueForNormalization() throws IOException {
			return 1f;
		}

		@Override
		public void normalize(float norm, float boost) {
		}

		@Override
		public Scorer scorer(LeafReaderContext context) throws IOException {
			return new GeoAdaptiveDistanceScorer(context.reader(), context.reader().getLiveDocs(), this);
		}

	}

	@Override
	public Weight createWeight(IndexSearcher searcher, boolean needsScores) {
		return new GeoAdaptiveDistanceWeight(this);
	}

	@Override
	public String toString(String field) {
		StringBuilder buffer = new StringBuilder();
		buffer.append(fieldName);
		buffer.append(",");
		buffer.append(pointParam.toString());
		buffer.append(ToStringUtils.boost(getBoost()));
		return buffer.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GeoAdaptiveDistanceQuery))
			return false;
		GeoAdaptiveDistanceQuery other = (GeoAdaptiveDistanceQuery) o;
		return (this.getBoost() == other.getBoost()) && other.fieldName.equals(fieldName)
				&& other.pointParam.equals(pointParam);
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + fieldName.hashCode();
		result = 31 * result + pointParam.hashCode();
		result = Float.floatToIntBits(getBoost()) ^ result;
		return result;
	}

}

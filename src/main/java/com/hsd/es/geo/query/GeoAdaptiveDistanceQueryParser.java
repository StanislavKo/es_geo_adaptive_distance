/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.hsd.es.geo.query;

import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.Version;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.geo.GeoUtils;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.fielddata.IndexGeoPointFieldData;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.geo.GeoPointFieldMapper;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParsingException;
import org.elasticsearch.index.query.RangeQueryParser;

import com.hsd.es.geo.utils.pojo.Curve;

import org.elasticsearch.index.get.GetField;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.index.query.QueryParsingException;

public class GeoAdaptiveDistanceQueryParser extends RangeQueryParser {

	public static final String NAME = "geo_adaptive_distance";

	@Override
	public String[] names() {
		return new String[] { NAME };
	}

	private Client client;

	@Inject
	public GeoAdaptiveDistanceQueryParser(Client client) {
		this.client = client;
	}

	@Override
	public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
		XContentParser parser = parseContext.parser();

		XContentParser.Token token;

		String queryName = null;
		String currentFieldName = null;
		GeoPoint point = new GeoPoint();
		String fieldName = null;
		Object vDistance = null;
		double distance;
		DistanceUnit unit = DistanceUnit.DEFAULT;
		GeoDistance geoDistance = GeoDistance.DEFAULT;
		String optimizeBbox = "memory";
		float boost = 1.0f;
		Curve curve = Curve.LINEAR;
		final boolean indexCreatedBeforeV2_0 = parseContext.indexVersionCreated().before(Version.V_2_0_0);
		boolean coerce = false;
		boolean ignoreMalformed = false;
		while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
			if (token == XContentParser.Token.FIELD_NAME) {
				currentFieldName = parser.currentName();
			} else if (parseContext.isDeprecatedSetting(currentFieldName)) {
				// skip
			} else if (token == XContentParser.Token.START_ARRAY) {
				fieldName = currentFieldName;
				GeoUtils.parseGeoPoint(parser, point);
			} else if (token == XContentParser.Token.START_OBJECT) {
				// the json in the format of -> field : { lat : 30, lon : 12 }
				String currentName = parser.currentName();
				fieldName = currentFieldName;
				while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
					if (token == XContentParser.Token.FIELD_NAME) {
						currentName = parser.currentName();
					} else if (token.isValue()) {
						if (currentName.equals(GeoPointFieldMapper.Names.LAT)) {
							point.resetLat(parser.doubleValue());
						} else if (currentName.equals(GeoPointFieldMapper.Names.LON)) {
							point.resetLon(parser.doubleValue());
						} else if (currentName.equals(GeoPointFieldMapper.Names.GEOHASH)) {
							point.resetFromGeoHash(parser.text());
						} else {
							throw new QueryParsingException(parseContext, "[geo_distance] query does not support [" + currentFieldName + "]");
						}
					}
				}
			} else if (token.isValue()) {
				if (currentFieldName.equals("distance")) {
					if (token == XContentParser.Token.VALUE_STRING) {
						vDistance = parser.text(); // a String
					} else {
						vDistance = parser.numberValue(); // a Number
					}
				} else if (currentFieldName.equals("unit")) {
					unit = DistanceUnit.fromString(parser.text());
				} else if (currentFieldName.equals("distance_type") || currentFieldName.equals("distanceType")) {
					geoDistance = GeoDistance.fromString(parser.text());
				} else if (currentFieldName.endsWith(GeoPointFieldMapper.Names.LAT_SUFFIX)) {
					point.resetLat(parser.doubleValue());
					fieldName = currentFieldName.substring(0, currentFieldName.length() - GeoPointFieldMapper.Names.LAT_SUFFIX.length());
				} else if (currentFieldName.endsWith(GeoPointFieldMapper.Names.LON_SUFFIX)) {
					point.resetLon(parser.doubleValue());
					fieldName = currentFieldName.substring(0, currentFieldName.length() - GeoPointFieldMapper.Names.LON_SUFFIX.length());
				} else if (currentFieldName.endsWith(GeoPointFieldMapper.Names.GEOHASH_SUFFIX)) {
					point.resetFromGeoHash(parser.text());
					fieldName = currentFieldName.substring(0, currentFieldName.length() - GeoPointFieldMapper.Names.GEOHASH_SUFFIX.length());
				} else if ("_name".equals(currentFieldName)) {
					queryName = parser.text();
				} else if ("optimize_bbox".equals(currentFieldName) || "optimizeBbox".equals(currentFieldName)) {
					optimizeBbox = parser.textOrNull();
				} else if ("boost".equals(currentFieldName)) {
					boost = parser.floatValue();
				} else if ("curve".equals(currentFieldName)) {
					curve = Curve.valueOf(parser.text().toUpperCase());
				} else if ("coerce".equals(currentFieldName) || (indexCreatedBeforeV2_0 && "normalize".equals(currentFieldName))) {
					coerce = parser.booleanValue();
					if (coerce == true) {
						ignoreMalformed = true;
					}
				} else if ("ignore_malformed".equals(currentFieldName) && coerce == false) {
					ignoreMalformed = parser.booleanValue();
				} else {
					point.resetFromString(parser.text());
					fieldName = currentFieldName;
				}
			}
		}

		// validation was not available prior to 2.x, so to support bwc
		// percolation queries we only ignore_malformed on 2.x created indexes
		if (!indexCreatedBeforeV2_0 && !ignoreMalformed) {
			if (point.lat() > 90.0 || point.lat() < -90.0) {
				throw new QueryParsingException(parseContext, "illegal latitude value [{}] for [{}]", point.lat(), NAME);
			}
			if (point.lon() > 180.0 || point.lon() < -180) {
				throw new QueryParsingException(parseContext, "illegal longitude value [{}] for [{}]", point.lon(), NAME);
			}
		}

		if (coerce) {
			GeoUtils.normalizePoint(point, coerce, coerce);
		}

		if (vDistance == null) {
			throw new QueryParsingException(parseContext, "geo_adaptive_distance requires 'distance' to be specified");
		} else if (vDistance instanceof Number) {
			distance = DistanceUnit.DEFAULT.convert(((Number) vDistance).doubleValue(), unit);
		} else {
			distance = DistanceUnit.parse((String) vDistance, unit, DistanceUnit.DEFAULT);
		}
		distance = geoDistance.normalize(distance, DistanceUnit.DEFAULT);

		MappedFieldType fieldType = parseContext.fieldMapper(fieldName);
		if (fieldType == null) {
			throw new QueryParsingException(parseContext, "failed to find geo_point field [" + fieldName + "]");
		}
		if (!(fieldType instanceof GeoPointFieldMapper.GeoPointFieldType)) {
			throw new QueryParsingException(parseContext, "field [" + fieldName + "] is not a geo_point field");
		}
		GeoPointFieldMapper.GeoPointFieldType geoFieldType = ((GeoPointFieldMapper.GeoPointFieldType) fieldType);

//		IndexGeoPointFieldData indexFieldData = parseContext.getForField(fieldType);
//		final Query query;
//		if (parseContext.indexVersionCreated().before(Version.V_2_2_0)) {
//			query = new GeoDistanceRangeQuery(point, null, distance, true, false, geoDistance, geoFieldType, indexFieldData, optimizeBbox);
//		} else {
//			distance = GeoUtils.maxRadialDistance(point, distance);
//			query = new GeoPointDistanceQuery(indexFieldData.getFieldNames().indexName(), point.lon(), point.lat(), distance);
//		}

//		System.out.println("[queryName:" + queryName + "] [currentFieldName:" + currentFieldName + "] [point:" + point + "] [fieldName:" + fieldName + "] "
//				+ "[vDistance:" + vDistance + "] [unit:" + unit + "] [geoDistance:" + geoDistance + "] [optimizeBbox:" + optimizeBbox + "] [curve:" + curve
//				+ "] [distance:" + distance + "]");

		return new GeoAdaptiveDistanceQuery(fieldName, point, distance, curve, boost);
	}

}

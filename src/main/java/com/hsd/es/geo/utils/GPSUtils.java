package com.hsd.es.geo.utils;

import org.elasticsearch.common.geo.GeoUtils;

public class GPSUtils {
	
	public static Double getDistance(Double latIn1, Double lonIn1, Double latIn2, Double lonIn2) {
		double distDegree = Math.sqrt((latIn1 - latIn2) * (latIn1 - latIn2)
				+ (lonIn1 - lonIn2) * (lonIn1 - lonIn2));
//		double radius = 6372795;
		double radius = GeoUtils.earthDiameter(latIn1)/2;

		double lat1 = latIn2 * Math.PI / 180;
		double lat2 = latIn1 * Math.PI / 180;
		double long1 = lonIn2 * Math.PI / 180;
		double long2 = lonIn1 * Math.PI / 180;
		double dLat = lat1 - lat2;
		double dLon = long1 - long2;

		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double d = radius * c;
		// Log.w("GPSUtils", "getDistance(), [lat1:" + latitude + "], [long1:" +
		// longitude + "], [lat2:" + lastLoc.getLatitude() + "], [long2:" +
		// lastLoc.getLongitude() + "], [dist:" + d + "]");
		return d;
	}

}

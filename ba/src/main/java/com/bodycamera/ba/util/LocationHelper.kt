package com.bodycamera.ba.util

import kotlin.math.*

object LocationHelper {
    /**地球半径6378.137，单位为千米  */
    private const val EARTH_RADIUS = 6378.137

    /**圆周率PI  */
    private const val PI = Math.PI

    /**卫星椭球坐标投影到平面地图坐标系的投影因子 */
    private const val AXIS = 6378245.0

    /**椭球的偏心率(a^2 - b^2) / a^2  */
    private const val OFFSET = 0.00669342162296594323

    /**圆周率转换量 */
    private const val X_PI = PI * 3000.0 / 180.0

    private fun rad(d: Double): Double {
        return d * PI / 180.0
    }

    /**
     * 计算两坐标点间的距离
     * @param lat1 坐标1维度
     * @param lng1 坐标1经度
     * @param lat2 坐标2维度
     * @param lng2 坐标2经度
     * @return double 单位 KM
     */
    fun getDistance1(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val radLat1: Double = rad(lat1)
        val radLat2: Double = rad(lat2)
        val a = radLat1 - radLat2
        val b: Double = rad(lng1) - rad(lng2)
        var s = 2 * asin(
            sqrt(sin(a / 2).pow(2.0) + cos(radLat1) * cos(radLat2) * sin(b / 2).pow(2.0))
        )
        s *= EARTH_RADIUS
        // s = Math.round(s * 10000) / 10000;
        // s = Math.round(s * 10000d) / 10000d;
        return s
    }

    /**
     *
     *
     * gcj02_bd09方法主要用于-GCJ02坐标转换为百度09坐标.
     *
     *
     * @param lat gcj02维度
     * @param lng gcj02经度
     * @return latlng[] 经纬数组（bd09）
     */
    fun gcj02_bd09(lat: Double, lng: Double): DoubleArray {
        val latlng = DoubleArray(2)
        val z =
            sqrt(lng * lng + lat * lat) + 0.00002 * sin(lat * X_PI)
        val theta = atan2(
            lat,
            lng
        ) + 0.000003 * cos(lng * X_PI)
        latlng[0] = z * sin(theta) + 0.006
        latlng[1] = z * cos(theta) + 0.0065
        return latlng
    }


    /**
     *
     *
     * bd09_gcj02方法主要用于-百度09坐标转转换为GCJ02坐标.
     *
     * @param lat bd09维度
     * @param lng bd09经度
     * @return latlng[] 维经数组（gcj02）
     */
    fun bd09_gcj02(lat: Double, lng: Double): DoubleArray {
        val x = lng - 0.0065
        val y = lat - 0.006
        val latlng = DoubleArray(2)
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta =
            atan2(y, x) - 0.000003 * cos(x * X_PI)
        latlng[0] = z * sin(theta)
        latlng[1] = z * cos(theta)
        return latlng
    }

    /**
     *
     *
     * bd09_wgs84方法主要用于-BD09坐标转为WGS84(地球坐标系).
     * <br></br>转换过程：bd09->gcj02->wgs84.
     *
     * @param lat 维度 （bd09）
     * @param lng 经度（bd09）
     * @return latlng[] 维经数组（wgs84）
     */
    fun bd09_wgs84(lat: Double, lng: Double): DoubleArray {
        val latlng: DoubleArray = bd09_gcj02(lat, lng)
        return gcj02_wgs84_2(latlng[0], latlng[1])
    }

    /**
     *
     *
     * gcj02_wgs84_2方法主要用于-gcj02坐标转为地球坐标wgs84（精确）.
     *
     *
     * @param lat 维度（gcj02）
     * @param lng 经度（gcj02）
     * @return latlng[] 维经数组（wgs84）
     */
    fun gcj02_wgs84_2(lat: Double, lng: Double): DoubleArray {
        val initDelta = 0.01
        val threshold = 0.000000001
        var dLat = initDelta
        var dLon = initDelta
        var mLat = lat - dLat
        var mLon = lng - dLon
        var pLat = lat + dLat
        var pLon = lng + dLon
        var wgsLat: Double
        var wgsLon: Double
        var i = 0.0
        while (true) {
            wgsLat = (mLat + pLat) / 2
            wgsLon = (mLon + pLon) / 2
            val tmp: DoubleArray = wgs84_gcj02(wgsLat, wgsLon)
            dLat = tmp[0] - lat
            dLon = tmp[1] - lng
            if (abs(dLat) < threshold && abs(dLon) < threshold) {
                break
            }
            if (dLat > 0) {
                pLat = wgsLat
            } else {
                mLat = wgsLat
            }
            if (dLon > 0) {
                pLon = wgsLon
            } else {
                mLon = wgsLon
            }
            if (++i > 10000) {
                break
            }
        }
        val latlon = DoubleArray(2)
        latlon[0] = wgsLat
        latlon[1] = wgsLon
        return latlon
    }

    /**
     * wgs84_bd09方法主要用于-wgs84地球坐标换转为百度09坐标.
     * <br></br>转换过程：wgs84->gcj02->bd09.
     *
     * @param lat 维度（wgs84）
     * @param lng 经度（wgs84）
     * @return latlng[] 维经数组（bd09）
     */
    fun wgs84_bd09(lat: Double, lng: Double): DoubleArray? {
        val latlon: DoubleArray = wgs84_gcj02(lat, lng)
        return gcj02_bd09(latlon[0], latlon[1])
    }

    /**
     *
     *
     * wgs84_gcj02方法主要用于-wgs84地球坐标转换为gcj02.
     *
     *
     *
     * 城邑耕夫 2016-11-21 - 下午6:29:45
     *
     * @param lat 维度（wgs84）
     * @param lng 经度（wgs84）
     * @return latlng[] 维经数组（gcj-02）
     */
    fun wgs84_gcj02(lat: Double, lng: Double): DoubleArray {
        val latlon = DoubleArray(2)
        if (outOfChina(lat, lng)) {
            latlon[0] = lat
            latlon[1] = lng
            return latlon
        }
        val deltaD: DoubleArray = transform(lat, lng)
        latlon[0] = lat + deltaD[0]
        latlon[1] = lng + deltaD[1]
        return latlon
    }

    /**
     *
     *
     * transform方法主要用于-wgs84与gcj02的坐标转换.
     *
     *
     * @param lat 维度
     * @param lng 经度
     * @return double[] 两坐标系间的偏移
     */
    fun transform(lat: Double, lng: Double): DoubleArray {
        val latlng = DoubleArray(2)
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLon = transformLng(lng - 105.0, lat - 35.0)
        val radLat: Double = lat / 180.0 * PI
        var magic = Math.sin(radLat)
        magic = 1 - OFFSET * magic * magic
        val sqrtMagic = Math.sqrt(magic)
        dLat =
            dLat * 180.0 / (AXIS * (1 - OFFSET) / (magic * sqrtMagic) * PI)
        dLon =
            dLon * 180.0 / (AXIS / sqrtMagic * Math.cos(radLat) * PI)
        latlng[0] = dLat
        latlng[1] = dLon
        return latlng
    }

    fun transformLat(x: Double, y: Double): Double {
        var ret =
            -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(
            2.0 * x * PI
        )) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(
            y * PI / 30.0
        )) * 2.0 / 3.0
        return ret
    }

    fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(
            2.0 * x * PI
        )) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(
            x / 30.0 * PI
        )) * 2.0 / 3.0
        return ret
    }

    fun outOfChina(lat: Double, lon: Double): Boolean {
        if (lon < 72.004 || lon > 137.8347) {
            return true
        }
        return lat < 0.8293 || lat > 55.8271
    }


}
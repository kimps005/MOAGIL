package kr.moagil.vehicle.ui

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kr.moagil.vehicle.data.DriverSession
import kr.moagil.vehicle.data.Point
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun KakaoVehicleMap(
    session: DriverSession,
    javascriptKey: String,
    mapBaseUrl: String,
    assetBaseUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val origin = remember(mapBaseUrl) { webOrigin(mapBaseUrl) }
    val assetOrigin = remember(assetBaseUrl, origin) { webOrigin(assetBaseUrl) ?: origin }
    val key = javascriptKey.trim()
    var mapError by remember(origin, key) { mutableStateOf<String?>(null) }
    val html = remember(origin, assetOrigin, key) {
        if (origin == null || assetOrigin == null || key.isEmpty()) null else vehicleMapHtml(origin, assetOrigin, key)
    }
    val payload = remember(session.sessionId, session.version) { session.toKakaoMapPayload() }
    val webView = remember(origin, key, html) {
        html?.let { page ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.rgb(241, 244, 250))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                tag = VehicleMapBindingState()
                webViewClient = object : WebViewClient() {
                    private fun reportRemoteMapError(view: WebView, message: String) {
                        mapError = message
                        view.evaluateJavascript("window.moagilSetNativeMapError(${JSONObject.quote(message)});", null)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        if (url == "about:blank") return
                        (view.tag as? VehicleMapBindingState)?.pageLoaded(view)
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (!request.isForMainFrame && isKakaoMapResource(request.url, assetOrigin)) {
                            val host = request.url.host ?: "카카오 지도 서버"
                            val path = request.url.encodedPath.orEmpty()
                            val cause = error.description?.toString()?.takeIf { it.isNotBlank() }
                                ?: "원인 정보 없음"
                            val diagnostic = "$host$path · 페이지 ${origin ?: "origin 없음"} · ${error.errorCode}: $cause"
                            val message = if (
                                cause.contains("ERR_BLOCKED_BY_ORB", ignoreCase = true) &&
                                host.equals("dapi.kakao.com", ignoreCase = true)
                            ) {
                                "카카오가 현재 페이지 주소를 허용하지 않아 지도를 차단했어요. Kakao Developers의 JavaScript SDK 도메인에 ${origin ?: "앱의 고정 주소"}를 등록해 주세요. ($diagnostic)"
                            } else when (error.errorCode) {
                                WebViewClient.ERROR_HOST_LOOKUP -> "카카오 지도 서버 주소를 찾지 못했어요 ($diagnostic). 인터넷 또는 DNS 연결을 확인해 주세요."
                                WebViewClient.ERROR_CONNECT -> "카카오 지도 서버에 연결하지 못했어요 ($diagnostic). 인터넷 연결을 확인해 주세요."
                                else -> "카카오 지도 서버 요청이 실패했어요 ($diagnostic). 네트워크와 JavaScript 키 설정을 확인해 주세요."
                            }
                            reportRemoteMapError(view, message)
                        }
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if (!request.isForMainFrame && isKakaoMapResource(request.url, assetOrigin) && response.statusCode >= 400) {
                            val host = request.url.host ?: "카카오 지도 서버"
                            val path = request.url.encodedPath.orEmpty()
                            val message = "카카오 지도 서버가 요청을 거부했어요 ($host$path · 페이지 ${origin ?: "origin 없음"} · HTTP ${response.statusCode}). JavaScript 키의 등록 도메인과 지도 사용 설정을 확인해 주세요."
                            reportRemoteMapError(view, message)
                        }
                    }
                }
                loadDataWithBaseURL("$origin/", page, "text/html", "UTF-8", null)
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        when {
            key.isEmpty() -> MapMessage(
                title = "카카오 지도를 불러올 수 없어요",
                detail = "서버의 Kakao JavaScript 키 설정을 확인해 주세요.",
            )
            origin == null -> MapMessage(
                title = "지도 서버 주소를 확인해 주세요",
                detail = "차량 앱의 서버 주소에 http:// 또는 https:// 주소가 필요합니다.",
            )
            webView != null -> Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        (view.tag as? VehicleMapBindingState)?.update(view, "${session.sessionId}:${session.version}", payload)
                    },
                )
                mapError?.let { message ->
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFFF1F4FA)).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text(message, color = Color(0xFFA54646))
                        Button(
                            onClick = {
                                mapError = null
                                webView?.reload()
                            },
                            modifier = Modifier.padding(top = 12.dp),
                        ) { Text("다시 시도") }
                    }
                }
            }
            else -> MapMessage(
                title = "카카오 지도를 준비하고 있어요",
                detail = "잠시 후 다시 확인해 주세요.",
            )
        }
    }
}

@Composable
private fun MapMessage(title: String, detail: String) {
    Box(
        Modifier.fillMaxSize().background(Color(0xFFF1F4FA)).padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color(0xFF26334F), fontWeight = FontWeight.Bold)
            Text(detail, color = Color(0xFF64708A), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

private fun DriverSession.toKakaoMapPayload(): String {
    fun pointJson(point: Point) = JSONObject()
        .put("lat", point.lat)
        .put("lon", point.lon)
        .put("label", point.label)

    val order = selectedOrder.ifEmpty {
        route?.order?.takeIf { it.isNotEmpty() } ?: passengers.map { it.id }
    }
    val passengerJson = JSONArray()
    passengers.forEach { passenger ->
        passengerJson.put(
            JSONObject()
                .put("id", passenger.id)
                .put("displayName", passenger.displayName)
                .put("point", pointJson(passenger.point)),
        )
    }
    val geometry = JSONArray()
    route?.geometry?.forEach { point -> geometry.put(JSONArray().put(point.lon).put(point.lat)) }
    val routeJson = JSONObject()
        .put("order", JSONArray(order))
        .put("geometry", geometry)

    return JSONObject()
        .put("startPoint", pointJson(startPoint))
        .put("finalPoint", finalPoint?.let(::pointJson) ?: JSONObject.NULL)
        .put("passengers", passengerJson)
        .put("selectedOrder", JSONArray(order))
        .put("routeSnapshot", routeJson)
        .toString()
}

private fun webOrigin(value: String): String? = runCatching {
    val uri = Uri.parse(value.trim())
    val scheme = uri.scheme?.lowercase()
    val authority = uri.encodedAuthority
    if (scheme !in setOf("http", "https") || authority.isNullOrBlank()) null
    else "$scheme://$authority"
}.getOrNull()

private fun isKakaoMapResource(url: Uri, assetOrigin: String?): Boolean {
    val host = url.host?.lowercase() ?: return false
    if (
        host == "dapi.kakao.com" ||
        host == "kakaocdn.net" || host.endsWith(".kakaocdn.net") ||
        host == "daumcdn.net" || host.endsWith(".daumcdn.net")
    ) return true

    val asset = assetOrigin?.let { Uri.parse(it) } ?: return false
    return url.scheme == "https" &&
        host == asset.host?.lowercase() &&
        url.port == asset.port &&
        url.encodedPath in setOf("/utils.js", "/kakao-map.js")
}

private fun vehicleMapHtml(origin: String, assetOrigin: String, javascriptKey: String): String {
    val originLiteral = jsLiteral(origin)
    val assetOriginLiteral = jsLiteral(assetOrigin)
    val keyLiteral = jsLiteral(javascriptKey)
    return """
        <!doctype html>
        <html lang="ko">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
          <style>
            * { box-sizing: border-box; }
            html, body { width: 100%; height: 100%; min-height: 100%; margin: 0; overflow: hidden; }
            #map { position: fixed; inset: 0; width: 100vw; height: 100vh; overflow: hidden; }
            body { font-family: sans-serif; background: #f1f4fa; }
            #map-status { position: fixed; inset: 0; z-index: 5; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; width: 100vw; height: 100vh; padding: 20px; color: #64708a; text-align: center; font-size: 14px; line-height: 1.55; background: #f1f4fa; }
            #map-status[hidden] { display: none; }
            #map-status.error { color: #a54646; }
            .map-error-message { max-width: 620px; white-space: pre-wrap; }
            .map-retry { padding: 9px 18px; border: 0; border-radius: 18px; color: white; background: #647cf2; font: inherit; font-weight: 700; }
            .moagil-pin-wrap { background: none; border: 0; }
            .moagil-pin { width: 34px; height: 34px; display: grid; place-items: center; border: 2px solid #fff; border-radius: 50% 50% 50% 4px; transform: rotate(-45deg); color: #fff; background: #687ff2; box-shadow: 0 4px 12px rgba(50,62,111,.28); font-size: 11px; font-weight: 900; }
            .moagil-pin b { transform: rotate(45deg); }
            .moagil-pin-next { background: #7b8eff; }
            .moagil-pin-stop { background: #8594c8; }
            .moagil-pin-start { background: #424d68; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <div id="map-status">카카오 지도를 불러오는 중이에요.</div>
          <script>
            const mapOrigin = $originLiteral;
            const mapAssetOrigin = $assetOriginLiteral;
            const kakaoKey = $keyLiteral;
            let pendingSession = null;
            let mapController = null;
            let mapStage = '지도 설정';
            let mapLoadGeneration = 0;
            const status = document.getElementById('map-status');
            function displayMapError(message) {
              const description = document.createElement('div');
              description.className = 'map-error-message';
              description.textContent = message || '카카오 지도 서버에 연결하지 못했어요. 인터넷/DNS와 JavaScript 키의 등록 도메인을 확인해 주세요.';
              const retry = document.createElement('button');
              retry.type = 'button';
              retry.className = 'map-retry';
              retry.textContent = '다시 시도';
              retry.addEventListener('click', startMapLoad);
              status.replaceChildren(description, retry);
              status.className = 'error';
              status.hidden = false;
            }
            window.moagilSetNativeMapError = function(message) {
              window.moagilNativeMapError = true;
              displayMapError(message);
            };
            function setMapStage(message) {
              mapStage = message;
              status.replaceChildren(document.createTextNode(message));
              status.className = '';
              status.hidden = false;
            }
            function showMapError(message) {
              if (window.moagilNativeMapError) return;
              displayMapError(message);
            }
            function layoutMapViewport() {
              const width = window.innerWidth;
              const height = window.innerHeight;
              if (!width || !height) return;
              document.documentElement.style.width = width + 'px';
              document.documentElement.style.height = height + 'px';
              document.body.style.width = width + 'px';
              document.body.style.height = height + 'px';
              document.body.style.position = 'relative';
              const element = document.getElementById('map');
              element.style.width = width + 'px';
              element.style.height = height + 'px';
              const mapStatus = document.getElementById('map-status');
              mapStatus.style.position = 'absolute';
              mapStatus.style.top = '0px';
              mapStatus.style.left = '0px';
              mapStatus.style.width = width + 'px';
              mapStatus.style.height = height + 'px';
              if (mapController?.map) mapController.map.relayout();
            }
            function hasRoadMapTiles() {
              return performance.getEntriesByType('resource').some(function(resource) {
                try {
                  const url = new URL(resource.name);
                  return /(^|\.)map\d*\.daumcdn\.net$/i.test(url.hostname) && /\/map_2d\//i.test(url.pathname);
                } catch (_) {
                  return false;
                }
              });
            }
            window.addEventListener('resize', layoutMapViewport);
            function addScript(src, onload, onerror) {
              const script = document.createElement('script');
              script.src = src;
              script.onload = onload;
              script.onerror = onerror;
              document.head.appendChild(script);
            }
            function renderSession() {
              if (!pendingSession || !window.MOAGILKakaoMaps || !window.kakao?.maps?.Map) return false;
              try {
                if (mapController) mapController.destroy();
                const mapElement = document.getElementById('map');
                mapElement.replaceChildren();
                layoutMapViewport();
                mapController = window.MOAGILKakaoMaps.createDriverMap(mapElement, pendingSession);
                window.kakao.maps.event.addListener(mapController.map, 'tilesloaded', function() {
                  setTimeout(function() {
                    if (hasRoadMapTiles() && !window.moagilNativeMapError) {
                      status.className = '';
                      status.hidden = true;
                    }
                  }, 200);
                });
                const generation = mapLoadGeneration;
                setTimeout(function() {
                  if (generation === mapLoadGeneration && mapController && !hasRoadMapTiles() && !window.moagilNativeMapError) {
                    showMapError('카카오 도로 타일이 없습니다.\n등록할 주소: ' + location.origin + '\nKakao Developers의 카카오맵 API 상태도 ON으로 설정해 주세요.');
                  }
                }, 12000);
                status.hidden = true;
                requestAnimationFrame(layoutMapViewport);
                return true;
              } catch (error) {
                showMapError('카카오 지도를 초기화하지 못했습니다. ' + (error?.message || ''));
                return false;
              }
            }
            window.moagilSetSession = function(serializedSession) {
              try {
                const nextSession = JSON.parse(serializedSession);
                if (mapController && JSON.stringify(nextSession) === JSON.stringify(pendingSession)) return;
                pendingSession = nextSession;
                renderSession();
              } catch (_) {
                showMapError('차량 경로 정보를 지도에 표시하지 못했습니다.');
              }
            };
            function startMapLoad() {
              const generation = ++mapLoadGeneration;
              window.moagilNativeMapError = false;
              layoutMapViewport();
              setMapStage('지도 설정을 불러오는 중이에요.');
              addScript(mapAssetOrigin + '/utils.js', function() {
                if (generation !== mapLoadGeneration) return;
                setMapStage('카카오 지도에 연결하고 있어요.');
                addScript('https://dapi.kakao.com/v2/maps/sdk.js?appkey=' + encodeURIComponent(kakaoKey) + '&autoload=false', function() {
                  if (generation !== mapLoadGeneration) return;
                  if (!window.kakao?.maps?.load) {
                    showMapError('카카오 SDK는 응답했지만 지도 로더를 준비하지 못했습니다. Kakao Map 사용 설정을 확인해 주세요.');
                    return;
                  }
                  window.kakao.maps.load(function() {
                    if (generation !== mapLoadGeneration) return;
                    if (!window.kakao?.maps?.Map) {
                      showMapError('카카오 SDK는 응답했지만 지도 기능을 초기화하지 못했습니다. Kakao Map 사용 설정을 확인해 주세요.');
                      return;
                    }
                    setMapStage('지도 화면을 불러오는 중이에요.');
                    addScript(mapAssetOrigin + '/kakao-map.js', function() {
                      if (generation !== mapLoadGeneration) return;
                      if (!window.MOAGILKakaoMaps) {
                        showMapError('카카오 지도 모듈을 초기화하지 못했습니다.');
                        return;
                      }
                      setMapStage('등록된 위치를 지도에 표시하고 있어요.');
                      if (pendingSession && !renderSession()) showMapError('경로 정보를 카카오 지도에 표시하지 못했습니다.');
                    }, function() {
                      if (generation === mapLoadGeneration) showMapError('카카오 지도 화면 파일을 불러오지 못했습니다. 서버 연결을 확인해 주세요.');
                    });
                  });
                }, function() {
                  if (generation === mapLoadGeneration) showMapError('카카오 지도 SDK 연결에 실패했습니다. 인터넷, DNS, 키와 등록 도메인을 확인해 주세요.');
                });
              }, function() {
                if (generation === mapLoadGeneration) showMapError('지도 설정 파일을 불러오지 못했습니다. 서버 주소를 확인해 주세요.');
              });
              setTimeout(function() {
                if (generation === mapLoadGeneration && !mapController && !status.classList.contains('error')) {
                  showMapError(mapStage + ' 단계에서 멈췄어요. 인터넷/DNS 연결과 카카오맵 설정을 확인해 주세요.');
                }
              }, 12000);
            }
            startMapLoad();
            window.addEventListener('error', function(event) {
              showMapError('지도 화면에서 오류가 발생했습니다. ' + (event.message || ''));
            });
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun jsLiteral(value: String): String = JSONObject.quote(value)
    .replace("<", "\\u003c")
    .replace(">", "\\u003e")
    .replace("&", "\\u0026")

private class VehicleMapBindingState {
    private var signature = ""
    private var payload = ""
    private var pageLoaded = false

    fun update(webView: WebView, nextSignature: String, nextPayload: String) {
        if (signature != nextSignature || payload != nextPayload) {
            signature = nextSignature
            payload = nextPayload
            if (pageLoaded) sendSession(webView)
        }
    }

    fun pageLoaded(webView: WebView) {
        pageLoaded = true
        sendSession(webView)
    }

    private fun sendSession(webView: WebView) {
        if (payload.isNotEmpty()) {
            webView.evaluateJavascript("window.moagilSetSession(${jsLiteral(payload)});", null)
        }
    }
}

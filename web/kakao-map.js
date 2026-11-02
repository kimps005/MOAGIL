/* Kakao Maps JavaScript API adapter for the web maps. */
(() => {
  const { passengerEmoji, escapeHtml } = window.MOAGILUtils;

  function latLng(point) {
    return new kakao.maps.LatLng(Number(point.lat), Number(point.lon));
  }

  function createMap(element, center, level) {
    const map = new kakao.maps.Map(element, {
      center: latLng(center),
      level,
      mapTypeId: kakao.maps.MapTypeId.ROADMAP,
    });
    element.dataset.mapProvider = 'kakao';
    map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
    setTimeout(() => map.relayout(), 0);
    return map;
  }

  function addLabel(map, point, label, kind, title) {
    if (!point || !Number.isFinite(Number(point.lat)) || !Number.isFinite(Number(point.lon))) return null;
    const content = '<span class="moagil-pin-wrap" title="' + escapeHtml(title || label) + '"><span class="moagil-pin moagil-pin-' + kind + '"><b>' + escapeHtml(label) + '</b></span></span>';
    const overlay = new kakao.maps.CustomOverlay({
      position: latLng(point),
      content,
      xAnchor: 0.5,
      yAnchor: 1,
      zIndex: 300,
    });
    overlay.setMap(map);
    return overlay;
  }

  function addDriverPoint(map, point, label, kind, title, overlays, bounds) {
    const overlay = addLabel(map, point, label, kind, title);
    if (!overlay) return;
    overlays.push(overlay);
    bounds.push(latLng(point));
  }

  function fitMap(map, points) {
    if (points.length > 1) {
      const bounds = new kakao.maps.LatLngBounds();
      points.forEach(point => bounds.extend(point));
      map.setBounds(bounds);
    } else if (points.length === 1) {
      map.setCenter(points[0]);
    }
  }

  function createDriverMap(element, session) {
    const start = session?.startPoint || { lat: 37.5665, lon: 126.9780 };
    const map = createMap(element, start, 6);
    const overlays = [];
    if (!session) return { map, destroy: () => map.relayout() };

    const bounds = [];
    const passengers = session.passengers || [];
    const passengerById = new Map(passengers.map(person => [person.id, person]));
    const order = session.selectedOrder || session.routeSnapshot?.order || passengers.map(person => person.id);

    addDriverPoint(map, start, '출발', 'start', '출발 위치', overlays, bounds);
    order.forEach((id, index) => {
      const person = passengerById.get(id);
      if (person) {
        addDriverPoint(
          map,
          person.point,
          passengerEmoji(person.displayName) || String(index + 1),
          index === 0 ? 'next' : 'stop',
          person.displayName || '하차 지점',
          overlays,
          bounds,
        );
      }
    });
    if (session.finalPoint) addDriverPoint(map, session.finalPoint, '도착', 'start', '최종 목적지', overlays, bounds);

    const geometry = session.routeSnapshot?.geometry;
    if (Array.isArray(geometry) && geometry.length > 1) {
      const route = geometry
        .map(pair => new kakao.maps.LatLng(Number(pair[1]), Number(pair[0])))
        .filter(point => Number.isFinite(point.getLat()) && Number.isFinite(point.getLng()));
      if (route.length > 1) {
        const outline = new kakao.maps.Polyline({
          map,
          path: route,
          strokeWeight: 9,
          strokeColor: '#ffffff',
          strokeOpacity: 0.9,
          strokeStyle: 'solid',
        });
        const line = new kakao.maps.Polyline({
          map,
          path: route,
          strokeWeight: 5,
          strokeColor: '#647cf2',
          strokeOpacity: 0.95,
          strokeStyle: 'solid',
        });
        overlays.push(outline, line);
        bounds.push(...route);
      }
    }

    fitMap(map, bounds);
    return {
      map,
      destroy: () => overlays.forEach(overlay => overlay.setMap(null)),
    };
  }

  function createPassengerMap(element, point, onChange) {
    const map = createMap(element, point, 4);
    const marker = new kakao.maps.Marker({
      map,
      position: latLng(point),
      draggable: true,
      title: '하차 위치 · 끌어서 이동',
    });
    const update = (position, pan = false) => {
      marker.setPosition(position);
      if (pan) map.panTo(position);
      onChange({ lat: +position.getLat().toFixed(6), lon: +position.getLng().toFixed(6) });
    };
    const onMapClick = event => update(event.latLng, true);
    const onMarkerDragEnd = () => update(marker.getPosition());
    kakao.maps.event.addListener(map, 'click', onMapClick);
    kakao.maps.event.addListener(marker, 'dragend', onMarkerDragEnd);

    return {
      map,
      setPoint: (next, pan = true) => update(latLng(next), pan),
      destroy: () => {
        kakao.maps.event.removeListener(map, 'click', onMapClick);
        kakao.maps.event.removeListener(marker, 'dragend', onMarkerDragEnd);
        marker.setMap(null);
      },
    };
  }

  window.MOAGILKakaoMaps = { createDriverMap, createPassengerMap };
})();

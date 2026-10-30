/* NAVER Maps JavaScript API v3 adapter. Loaded only when an NCP Client ID is configured. */
(() => {
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]);
  }

  function latLng(point) {
    return new naver.maps.LatLng(Number(point.lat), Number(point.lon));
  }

  function icon(label, kind) {
    return {
      content: '<span class="moagil-pin moagil-pin-' + kind + '"><b>' + escapeHtml(label) + '</b></span>',
      size: new naver.maps.Size(34, 42),
      anchor: new naver.maps.Point(17, 38),
    };
  }

  function createMap(element, center, zoom) {
    const map = new naver.maps.Map(element, {
      center: latLng(center),
      zoom,
      minZoom: 6,
      maxZoom: 21,
      zoomControl: true,
      zoomControlOptions: { position: naver.maps.Position.TOP_RIGHT },
      mapDataControl: true,
      logoControl: true,
    });
    setTimeout(() => naver.maps.Event.trigger(map, 'resize'), 0);
    return map;
  }

  function addMarker(map, point, label, kind, title) {
    if (!point || !Number.isFinite(Number(point.lat)) || !Number.isFinite(Number(point.lon))) return null;
    return new naver.maps.Marker({
      map,
      position: latLng(point),
      icon: icon(label, kind),
      title: title || label,
      zIndex: 300,
    });
  }

  function createDriverMap(element, session) {
    const start = session?.startPoint || { lat: 37.5665, lon: 126.9780 };
    const map = createMap(element, start, 13);
    const overlays = [];
    if (session) {
      const passengers = session.passengers || [];
      const passengerById = new Map(passengers.map(person => [person.id, person]));
      const order = session.selectedOrder || session.routeSnapshot?.order || passengers.map(person => person.id);
      const bounds = new naver.maps.LatLngBounds();
      const add = (point, label, kind, title) => {
        const marker = addMarker(map, point, label, kind, title);
        if (marker) { overlays.push(marker); bounds.extend(marker.getPosition()); }
      };

      add(start, '출발', 'start', '출발 위치');
      order.forEach((id, index) => {
        const person = passengerById.get(id);
        if (person) add(person.point, String(index + 1), index === 0 ? 'next' : 'stop', person.displayName || '하차 지점');
      });
      if (session.finalPoint) add(session.finalPoint, '도착', 'start', '최종 목적지');

      const geometry = session.routeSnapshot?.geometry;
      if (Array.isArray(geometry) && geometry.length > 1) {
        const path = geometry.map(pair => new naver.maps.LatLng(Number(pair[1]), Number(pair[0]))).filter(point => Number.isFinite(point.lat()) && Number.isFinite(point.lng()));
        if (path.length > 1) {
          const route = new naver.maps.Polyline({ map, path, strokeColor: '#138f85', strokeWeight: 6, strokeOpacity: 0.94, strokeLineCap: 'round', strokeLineJoin: 'round', zIndex: 100 });
          overlays.push(route);
          path.forEach(point => bounds.extend(point));
        }
      }
      if (!bounds.isEmpty()) map.fitBounds(bounds, { top: 78, right: 32, bottom: 90, left: 32 });
    }
    return { map, destroy: () => { overlays.forEach(overlay => overlay.setMap(null)); map.destroy?.(); } };
  }

  function createPassengerMap(element, point, onChange) {
    const map = createMap(element, point, 16);
    const marker = addMarker(map, point, '핀', 'next', '하차 위치 · 끌어서 이동');
    marker.setDraggable(true);
    const update = position => {
      marker.setPosition(position);
      const next = { lat: +position.lat().toFixed(6), lon: +position.lng().toFixed(6) };
      onChange(next);
    };
    naver.maps.Event.addListener(map, 'click', event => {
      marker.setPosition(event.coord);
      map.panTo(event.coord);
      update(event.coord);
    });
    naver.maps.Event.addListener(marker, 'dragend', event => {
      const position = event.coord || marker.getPosition();
      update(position);
    });
    return {
      map,
      setPoint: (next, pan = true) => {
        const position = latLng(next);
        marker.setPosition(position);
        if (pan) map.panTo(position);
      },
      destroy: () => { marker.setMap(null); map.destroy?.(); },
    };
  }

  window.MOAGILNaverMaps = { createDriverMap, createPassengerMap };
})();

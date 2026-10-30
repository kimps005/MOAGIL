/* OpenStreetMap tiles and OSRM road geometries for the MOAGIL demo. */
(() => {
  const attribution = '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap contributors</a>';
  const tileUrl = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
  const avatarEmojis = ['🚗', '🚕', '🚙', '🚌', '🚲', '🐱', '🐶', '🐼', '🐰', '🦊', '🌸', '⭐'];

  function passengerEmoji(displayName) {
    return avatarEmojis.find(emoji => String(displayName || '').startsWith(emoji + ' ')) || '';
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]);
  }

  function createMap(element, center, zoom) {
    const map = L.map(element, { zoomControl: false, attributionControl: true, scrollWheelZoom: true });
    L.tileLayer(tileUrl, { maxZoom: 19, attribution }).addTo(map);
    L.control.zoom({ position: 'bottomleft' }).addTo(map);
    map.setView(center, zoom);
    setTimeout(() => map.invalidateSize(), 0);
    return map;
  }

  function icon(label, kind) {
    const className = 'moagil-pin moagil-pin-' + kind;
    return L.divIcon({
      className: 'moagil-pin-wrap',
      html: '<span class="' + className + '"><b>' + escapeHtml(label) + '</b></span>',
      iconSize: [34, 42],
      iconAnchor: [17, 38],
    });
  }

  function addMarker(map, point, label, kind, title) {
    if (!point || !Number.isFinite(Number(point.lat)) || !Number.isFinite(Number(point.lon))) return null;
    return L.marker([Number(point.lat), Number(point.lon)], { icon: icon(label, kind), title: title || label, keyboard: true }).addTo(map);
  }

  function createDriverMap(element, session) {
    const start = session?.startPoint || { lat: 37.5665, lon: 126.9780 };
    const map = createMap(element, [Number(start.lat), Number(start.lon)], 13);
    if (!session) return map;

    const passengers = session.passengers || [];
    const passengerById = new Map(passengers.map(person => [person.id, person]));
    const order = session.selectedOrder || session.routeSnapshot?.order || passengers.map(person => person.id);
    const bounds = [];
    const add = (point, label, kind, title) => {
      const marker = addMarker(map, point, label, kind, title);
      if (marker) bounds.push(marker.getLatLng());
    };

    add(start, '출발', 'start', '출발 위치');
    order.forEach((id, index) => {
      const person = passengerById.get(id);
      if (person) add(person.point, passengerEmoji(person.displayName) || String(index + 1), index === 0 ? 'next' : 'stop', person.displayName || '하차 지점');
    });
    if (session.finalPoint) add(session.finalPoint, '도착', 'start', '최종 목적지');

    const geometry = session.routeSnapshot?.geometry;
    if (Array.isArray(geometry) && geometry.length > 1) {
      const route = geometry.map(pair => [Number(pair[1]), Number(pair[0])]).filter(pair => Number.isFinite(pair[0]) && Number.isFinite(pair[1]));
      if (route.length > 1) {
        L.polyline(route, { color: '#ffffff', weight: 9, opacity: 0.9, lineCap: 'round', lineJoin: 'round' }).addTo(map);
        L.polyline(route, { color: '#647cf2', weight: 5, opacity: 0.95, lineCap: 'round', lineJoin: 'round' }).addTo(map);
        bounds.push(...route);
      }
    }

    if (bounds.length > 1) map.fitBounds(L.latLngBounds(bounds), { padding: [34, 34], maxZoom: 15 });
    else if (bounds.length === 1) map.setView(bounds[0], 14);
    return map;
  }

  function createPassengerMap(element, point, onChange) {
    const map = createMap(element, [Number(point.lat), Number(point.lon)], 15);
    const marker = addMarker(map, point, '핀', 'next', '하차 위치 · 끌어서 이동');
    const setPoint = (next, pan = true) => {
      const location = [Number(next.lat), Number(next.lon)];
      if (!location.every(Number.isFinite)) return;
      marker.setLatLng(location);
      if (pan) map.panTo(location);
      onChange({ lat: +location[0].toFixed(6), lon: +location[1].toFixed(6) });
    };
    marker.dragging.enable();
    marker.on('dragend', () => {
      const location = marker.getLatLng();
      onChange({ lat: +location.lat.toFixed(6), lon: +location.lng.toFixed(6) });
    });
    map.on('click', event => setPoint({ lat: event.latlng.lat, lon: event.latlng.lng }));
    return { map, setPoint };
  }

  window.MOAGILMaps = { createDriverMap, createPassengerMap };
})();

// Prueba de carga de POST /api/v1/geofence/validate con k6. Dos modos:
//  - Tasa constante: TPS peticiones/s durante DURACION (por defecto 10 TPS durante 30 s), con umbrales
//    de aceptación.
//  - Rampa: ETAPAS="100,200,400" sube a cada tasa en SUBIDA y la mantiene MANTENER; sin umbrales, el
//    análisis por etapa se hace sobre la serie exportada (--out csv).
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

const API = __ENV.API || 'http://geofence-api:8080/api/v1/geofence';
const TPS = Number(__ENV.TPS || 10);
const DURACION = __ENV.DURACION || '30s';
const ETAPAS = (__ENV.ETAPAS || '').split(',').filter(Boolean).map(Number);
const SUBIDA = __ENV.SUBIDA || '10s';
const MANTENER = __ENV.MANTENER || '20s';

const lugares = new SharedArray('lugares', () => JSON.parse(open('/datos/geocercas.json')));

const constante = {
  scenarios: {
    carga: {
      executor: 'constant-arrival-rate',
      rate: TPS, timeUnit: '1s', duration: DURACION,
      preAllocatedVUs: TPS, maxVUs: TPS * 5,
    },
  },
  thresholds: {
    'http_req_failed{scenario:carga}': ['rate<0.001'],
    'http_req_duration{scenario:carga}': ['p(95)<200', 'p(99)<500'],
    'checks{scenario:carga}': ['rate>0.999'],
    'dropped_iterations{scenario:carga}': ['count==0'],
  },
};

const rampa = {
  scenarios: {
    carga: {
      executor: 'ramping-arrival-rate',
      startRate: 0, timeUnit: '1s',
      preAllocatedVUs: 100, maxVUs: 1000,
      stages: ETAPAS.flatMap((t) => [{ target: t, duration: SUBIDA }, { target: t, duration: MANTENER }]),
    },
  },
};

export const options = {
  ...(ETAPAS.length ? rampa : constante),
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const params = { headers: { 'Content-Type': 'application/json' }, tags: { name: 'validate' } };

export default function () {
  let lat, lon, esperado = null;
  if (Math.random() < 0.7) {
    const l = lugares[Math.floor(Math.random() * lugares.length)];
    lat = l.latitud; lon = l.longitud; esperado = l.nombre;
  } else {
    lat = 14.5 + Math.random() * (32.7 - 14.5);
    lon = -118.4 + Math.random() * (118.4 - 86.7);
  }

  const res = http.post(`${API}/validate`, JSON.stringify({ latitud: lat, longitud: lon }), params);
  const cuerpo = res.status === 200 ? res.json() : null;

  check(res, {
    'status 200': (r) => r.status === 200,
    'dentro coincide con la lista': () => cuerpo !== null && cuerpo.dentro === cuerpo.geocercas.length > 0,
    'incluye la geocerca esperada': () =>
      esperado === null || (cuerpo !== null && cuerpo.geocercas.some((g) => g.nombre === esperado)),
  });
}

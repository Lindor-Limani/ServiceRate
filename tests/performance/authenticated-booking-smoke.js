import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    booking_smoke: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || '30s'
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800']
  }
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081/api';
const CUSTOMER_TOKEN = __ENV.CUSTOMER_TOKEN;
const SERVICE_ID = __ENV.SERVICE_ID;

export default function () {
  if (!CUSTOMER_TOKEN || !SERVICE_ID) {
    throw new Error('CUSTOMER_TOKEN and SERVICE_ID are required for authenticated booking smoke tests.');
  }

  const payload = JSON.stringify({
    serviceOfferingId: SERVICE_ID,
    bookingDate: futureDate()
  });
  const response = http.post(`${BASE_URL}/bookings`, payload, {
    headers: {
      Authorization: `Bearer ${CUSTOMER_TOKEN}`,
      'Content-Type': 'application/json'
    },
    tags: { endpoint: 'bookings' }
  });

  check(response, {
    'booking request is accepted or rejected with business validation only': r => [200, 400].includes(r.status),
    'booking response has no stack trace': r => !r.body.includes('java.lang')
  });
  sleep(1);
}

function futureDate() {
  const d = new Date();
  d.setDate(d.getDate() + 7);
  return d.toISOString().slice(0, 10);
}

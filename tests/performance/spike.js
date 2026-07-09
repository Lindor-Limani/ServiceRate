import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      stages: [
        { duration: '10s', target: 10 },
        { duration: '10s', target: 250 },
        { duration: '30s', target: 300 },
        { duration: '10s', target: 10 },
        { duration: '10s', target: 0 }
      ]
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1500']
  }
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081/api';

export default function () {
  const response = http.get(`${BASE_URL}/services?page=0&size=12&sort=priceAsc`, {
    tags: { endpoint: 'services' }
  });
  check(response, {
    'services survive spike': r => r.status === 200
  });
  sleep(0.5);
}

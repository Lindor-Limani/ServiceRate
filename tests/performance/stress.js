import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

export const errors = new Rate('errors');

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '30s', target: 0 }
      ]
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<1000'],
    errors: ['rate<0.02']
  }
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081/api';

export default function () {
  const searchTerms = ['rohr', 'garten', 'maler', 'strom', ''];
  const term = searchTerms[Math.floor(Math.random() * searchTerms.length)];
  const response = http.get(`${BASE_URL}/services?page=0&size=24&q=${encodeURIComponent(term)}&minRating=0`, {
    tags: { endpoint: 'services' }
  });
  errors.add(!check(response, {
    'service search remains successful under stress': r => r.status === 200
  }));
  sleep(Math.random() * 2);
}

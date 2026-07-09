import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

export const errorRate = new Rate('errors');

export const options = {
  scenarios: {
    normal_browsing: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || '1m'
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    'http_req_duration{endpoint:services}': ['p(95)<1000'],
    errors: ['rate<0.01']
  }
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081/api';

export default function () {
  const services = http.get(`${BASE_URL}/services?page=0&size=24&sort=recommended`, {
    tags: { endpoint: 'services' }
  });
  const ok = check(services, {
    'services status is 200': response => response.status === 200,
    'services response contains content': response => response.body.includes('content')
  });
  errorRate.add(!ok);

  const search = http.get(`${BASE_URL}/services?q=rohr&location=Wien&minRating=0`, {
    tags: { endpoint: 'services' }
  });
  errorRate.add(!check(search, {
    'search status is 200': response => response.status === 200
  }));

  sleep(1);
}

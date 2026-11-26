
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 1,
    duration: '30s',

    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
};

const BASE_URL = 'http://localhost:8080';

export default function () {

    const response = http.get(
        `${BASE_URL}/products?page=0&size=10&sortBy=id`
    );

    check(response, {
        'status is 200': (r) => r.status === 200,
        'response contains products': (r) =>
            r.json('content').length > 0,
        'response contains totalElements': (r) =>
            r.json('totalElements') !== undefined,
    });

    sleep(1);
}

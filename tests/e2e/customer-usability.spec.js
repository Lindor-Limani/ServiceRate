const { test, expect } = require('@playwright/test');

const categoryPayload = '<svg onload=alert("category-xss")></svg>';

const service = {
  id: '11111111-1111-4111-8111-111111111111',
  providerId: '22222222-2222-4222-8222-222222222222',
  providerName: 'Ada Builder',
  providerProfileImageUrl: null,
  title: 'Rohr reparieren',
  description: '<img src=x onerror=alert(1)> Bad und Kueche professionell reparieren',
  category: categoryPayload,
  price: 80,
  estimatedHours: 2,
  imageUrl: '',
  imageUrls: [],
  deliverableType: 'ON_SITE',
  status: 'ACTIVE',
  location: 'Wien',
  averageRating: 4.8,
  reviewCount: 12,
  trustScore: 92,
  providerPaypalAvailable: false,
  providerStripeAvailable: false,
  providerOfflinePaymentAvailable: true,
  reviews: [
    {
      id: '33333333-3333-4333-8333-333333333333',
      bookingId: '44444444-4444-4444-8444-444444444444',
      reviewerName: 'Grace Customer',
      serviceTitle: 'Rohr reparieren',
      rating: 5,
      comment: 'Sehr sauber gearbeitet.'
    }
  ]
};

async function mockCustomerApi(page) {
  await page.route('http://localhost:8081/api/weather/**', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        city: 'Wien',
        main: 'Clear',
        temperature: 22,
        description: 'klar'
      })
    });
  });

  await page.route('http://localhost:8081/api/services?**', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [service],
        totalElements: 1,
        totalPages: 1,
        page: 0,
        size: 24
      })
    });
  });

  await page.route(`http://localhost:8081/api/services/${service.id}`, route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(service)
    });
  });

  await page.route('http://localhost:8081/api/auth/login', async route => {
    const body = route.request().postDataJSON();
    if (body.email === 'customer@example.com' && body.password === 'Password123!') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          token: fakeJwt('CUSTOMER'),
          userId: '55555555-5555-4555-8555-555555555555',
          emailVerified: true
        })
      });
    }
    return route.fulfill({
      status: 401,
      contentType: 'text/plain',
      body: 'Falsches Passwort.'
    });
  });

  await page.route('http://localhost:8081/api/auth/register', route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        message: 'Konto erstellt. Bitte verifiziere deine E-Mail-Adresse ueber den Link in der Mail.',
        user: {
          id: '55555555-5555-4555-8555-555555555555',
          email: 'new@example.com',
          firstName: 'New',
          lastName: 'Customer',
          accountType: 'CUSTOMER',
          status: 'ACTIVE'
        }
      })
    });
  });

  await page.route('http://localhost:8081/api/bookings', async route => {
    const body = route.request().postDataJSON();
    if (!body.bookingDate) {
      return route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Bitte wähle einen Wunschtermin.' })
      });
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: '66666666-6666-4666-8666-666666666666',
        status: 'PENDING'
      })
    });
  });
}

test.beforeEach(async ({ page }) => {
  await mockCustomerApi(page);
});

test('customer can search services and XSS-like service text is rendered as text', async ({ page }) => {
  await page.goto('/customer-app.html');

  await expect(page.getByRole('heading', { name: /Finde den richtigen/i })).toBeVisible();
  await expect(page.getByText('Rohr reparieren')).toBeVisible();
  await expect(page.getByText('<img src=x onerror=alert(1)> Bad und Kueche professionell reparieren')).toBeVisible();
  await expect(page.getByText(categoryPayload, { exact: true }).first()).toBeVisible();
  await expect(page.locator('img[src="x"]')).toHaveCount(0);
  await expect(page.locator('svg[onload]')).toHaveCount(0);

  await page.getByPlaceholder(/Was brauchst du/i).fill('Rohr');
  await page.getByRole('button', { name: 'Suchen' }).click();
  await expect(page.getByText('1 gefunden')).toBeVisible();

  await page.getByText('Rohr reparieren').first().click();
  await page.waitForURL(/service-detail\.html/);
  await expect(page.getByText(categoryPayload, { exact: true })).toBeVisible();
  await expect(page.locator('svg[onload]')).toHaveCount(0);
});

test('registration and invalid login show understandable user feedback', async ({ page }) => {
  await page.goto('/customer-app.html');
  await page.getByRole('button', { name: 'Anmelden' }).click();
  await page.getByRole('button', { name: 'Registrieren' }).click();
  await page.locator('#regFirst').fill('New');
  await page.locator('#regLast').fill('Customer');
  await page.locator('#regEmail').fill('new@example.com');
  await page.locator('#regPassword').fill('Password123!');
  await page.getByRole('button', { name: 'Konto erstellen' }).click();

  await expect(page.getByText(/Konto erstellt/i)).toBeVisible();

  await page.locator('#tabLogin').click();
  await page.locator('#loginEmail').fill('customer@example.com');
  await page.locator('#loginPassword').fill('wrong');
  await page.locator('#loginForm').getByRole('button', { name: /^Anmelden$/ }).click();
  await expect(page.getByText(/Login fehlgeschlagen/i)).toBeVisible();
});

test('customer must choose a booking date before creating a booking', async ({ page }) => {
  await page.goto('/customer-app.html');
  await page.evaluate(token => {
    localStorage.setItem('customer_jwt', token);
    localStorage.setItem('customer_email_verified', 'true');
  }, fakeJwt('CUSTOMER'));
  await page.reload();

  await page.getByText('Rohr reparieren').first().click();
  await page.waitForURL(/service-detail\.html/);
  await page.route(`http://localhost:8081/api/services/${service.id}`, route => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(service)
    });
  });
  await expect(page.getByRole('heading', { name: 'Rohr reparieren' })).toBeVisible();
  await page.getByRole('button', { name: /Jetzt buchen/i }).click();

  await expect(page.getByText(/Bitte wähle einen Wunschtermin/i)).toBeVisible();
});

test('main customer UI is usable on desktop and mobile viewports', async ({ page }) => {
  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 390, height: 844 }
  ]) {
    await page.setViewportSize(viewport);
    await page.goto('/customer-app.html');
    await expect(page.getByRole('link', { name: /ServiceRate/i })).toBeVisible();
    await expect(page.getByPlaceholder(/Was brauchst du/i)).toBeVisible();
    await expect(page.getByText('Rohr reparieren')).toBeVisible();
  }
});

test('PayPal return sends only the bound state to the completion endpoint', async ({ page }) => {
  const requests = [];
  await page.route('http://localhost:8081/api/**', route => {
    const pathname = new URL(route.request().url()).pathname;
    const body = pathname === '/api/providers/me/paypal/onboarding-complete'
      ? route.request().postDataJSON()
      : null;
    requests.push({ pathname, body });
    if (pathname === '/api/providers/me/paypal/onboarding-complete') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          paypalMerchantId: 'verified-merchant',
          paypalEmail: 'verified@example.com',
          paypalOnboardingStatus: 'CONNECTED',
          paypalPermissionsGranted: true,
          paypalEmailConfirmed: true
        })
      });
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([])
    });
  });
  await page.addInitScript(token => {
    localStorage.setItem('provider_jwt', token);
    localStorage.setItem('provider_user_id', '22222222-2222-4222-8222-222222222222');
    localStorage.setItem('provider_paypal_onboarding_started', 'true');
  }, fakeJwt('PROVIDER'));

  await page.goto('/provider-dashboard.html?paypalOnboarding=return&state=bound-state&merchantIdInPayPal=attacker-merchant&permissionsGranted=true&isEmailConfirmed=true');

  await expect.poll(() => requests.filter(request => request.pathname === '/api/providers/me/paypal/onboarding-complete').length).toBe(1);
  const completion = requests.find(request => request.pathname === '/api/providers/me/paypal/onboarding-complete');
  expect(completion.body).toEqual({ state: 'bound-state' });
  expect(requests.map(request => request.pathname)).not.toContain('/api/providers/me/paypal/onboarding-status');
  expect(requests.map(request => request.pathname)).not.toContain('/api/providers/me/paypal/onboarding-return');
  await expect(page.locator('#providerPaypalConfirmBtn')).toHaveCount(0);
});

test('PayPal return without state is rejected without an API callback', async ({ page }) => {
  const requests = [];
  await page.route('http://localhost:8081/api/**', route => {
    requests.push(new URL(route.request().url()).pathname);
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([])
    });
  });
  await page.addInitScript(token => {
    localStorage.setItem('provider_jwt', token);
    localStorage.setItem('provider_paypal_onboarding_started', 'true');
  }, fakeJwt('PROVIDER'));

  await page.goto('/provider-dashboard.html?paypalOnboarding=return&merchantIdInPayPal=attacker-merchant');

  await expect(page).toHaveURL(/provider-dashboard\.html$/);
  await expect.poll(() => page.evaluate(() => localStorage.getItem('provider_paypal_onboarding_started'))).toBeNull();
  expect(requests).not.toContain('/api/providers/me/paypal/onboarding-complete');
  expect(requests).not.toContain('/api/providers/me/paypal/onboarding-status');
});

test('unbound PayPal identity codes are discarded without an API callback', async ({ page }) => {
  const requests = [];
  await page.route('http://localhost:8081/api/**', route => {
    requests.push(new URL(route.request().url()).pathname);
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([])
    });
  });
  await page.addInitScript(token => {
    localStorage.setItem('provider_jwt', token);
    localStorage.setItem('provider_user_id', '22222222-2222-4222-8222-222222222222');
    localStorage.setItem('provider_paypal_onboarding_started', 'true');
  }, fakeJwt('PROVIDER'));

  await page.goto('/provider-dashboard.html?code=attacker-code&state=');

  await expect(page).toHaveURL(/provider-dashboard\.html$/);
  await expect.poll(() => page.evaluate(() => localStorage.getItem('provider_paypal_onboarding_started'))).toBeNull();
  expect(requests).not.toContain('/api/providers/me/paypal/identity-return');
});

function fakeJwt(accountType) {
  const payload = Buffer.from(JSON.stringify({
    sub: `${accountType.toLowerCase()}@example.com`,
    accountType,
    exp: Math.floor(Date.now() / 1000) + 3600
  })).toString('base64url');
  return `test.${payload}.signature`;
}

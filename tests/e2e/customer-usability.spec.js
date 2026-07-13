const { test, expect } = require('@playwright/test');

const service = {
  id: '11111111-1111-4111-8111-111111111111',
  providerId: '22222222-2222-4222-8222-222222222222',
  providerName: 'Ada Builder',
  providerProfileImageUrl: null,
  title: 'Rohr reparieren',
  description: '<img src=x onerror=alert(1)> Bad und Kueche professionell reparieren',
  category: 'PLUMBING',
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
  await expect(page.locator('img[src="x"]')).toHaveCount(0);

  await page.getByPlaceholder(/Was brauchst du/i).fill('Rohr');
  await page.getByRole('button', { name: 'Suchen' }).click();
  await expect(page.getByText('1 gefunden')).toBeVisible();
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

function fakeJwt(accountType) {
  const payload = Buffer.from(JSON.stringify({
    sub: `${accountType.toLowerCase()}@example.com`,
    accountType,
    exp: Math.floor(Date.now() / 1000) + 3600
  })).toString('base64url');
  return `test.${payload}.signature`;
}

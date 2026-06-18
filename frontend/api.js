// api.js - Unsere Brücke zum Spring Boot Backend

const BASE_URL = 'http://localhost:8081/api';

// Diese Funktion führt die AJAX-Anfragen (Fetch) aus (Requirement M4)
async function fetchAPI(endpoint, method = 'GET', body = null, tokenKey = 'jwt_token') {
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json' // Wir wollen JSON zurück (Requirement M5)
  };

  // Wenn wir einen JWT-Token haben, hängen wir ihn wie einen Ausweis an.
  // tokenKey trennt die Sessions: Kunde ('customer_jwt') und Anbieter ('provider_jwt')
  const token = localStorage.getItem(tokenKey);
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const config = {
    method: method,
    headers: headers
  };

  if (body) {
    config.body = JSON.stringify(body);
  }

  try {
    const response = await fetch(`${BASE_URL}${endpoint}`, config);

    // Wenn das Backend einen Fehler wirft (z.B. Falsches Passwort)
    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(errorText || 'Ein Fehler ist aufgetreten');
    }

    // Manche Endpunkte (wie DELETE) geben keinen Text zurück
    if (response.status === 204 || response.headers.get('content-length') === '0') {
      return null;
    }

    return await response.json();
  } catch (error) {
    console.error("API Fehler:", error);
    throw error;
  }
}

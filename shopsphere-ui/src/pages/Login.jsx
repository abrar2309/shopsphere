
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { loginUser } from '../api/authApi';

export default function Login() {
  const navigate = useNavigate();

  const [form, setForm] = useState({
    email: '',
    password: '',
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function handleChange(event) {
    const { name, value } = event.target;

    setForm((previous) => ({
      ...previous,
      [name]: value,
    }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setLoading(true);

    try {
      const result = await loginUser({
        email: form.email.trim(),
        password: form.password,
      });

      if (!result.token) {
        throw new Error('Login succeeded but the server did not return a token.');
      }

      // Temporary local-development token storage.
      // JavaScript can access sessionStorage, so production needs
      // additional XSS protection and a carefully designed token strategy.
      sessionStorage.setItem('shopsphere_token', result.token);
      sessionStorage.setItem('shopsphere_email', result.email);
      sessionStorage.setItem('shopsphere_role', result.role);

      navigate('/', { replace: true });
    } catch (requestError) {
      const responseData = requestError.response?.data;

      const serverMessage =
        typeof responseData === 'string'
          ? responseData
          : responseData?.message || responseData?.error;

      setError(
        serverMessage ||
        requestError.message ||
        'Login failed. Check your email, password, and backend connection.'
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <Link className="auth-brand" to="/">
          ShopSphere
        </Link>

        <span className="eyebrow">WELCOME BACK</span>

        <h1>Log in to your account</h1>

        <p className="auth-description">
          Sign in to continue your ShopSphere experience.
        </p>

        <label>
          Email address
          <input
            name="email"
            type="email"
            value={form.email}
            onChange={handleChange}
            autoComplete="email"
            required
          />
        </label>

        <label>
          Password
          <input
            name="password"
            type="password"
            value={form.password}
            onChange={handleChange}
            autoComplete="current-password"
            required
          />
        </label>

        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}

        <button
          className="auth-submit"
          type="submit"
          disabled={loading}
        >
          {loading ? 'Logging in...' : 'Log in'}
        </button>

        <p className="auth-switch">
          New to ShopSphere? <Link to="/register">Create an account</Link>
        </p>
      </form>
    </main>
  );
}

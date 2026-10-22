
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { registerUser } from '../api/authApi';

export default function Register() {
  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phoneNumber: '',
    password: '',
  });

  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((previous) => ({ ...previous, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setMessage('');
    setError('');

    if (form.firstName.trim().length < 2 ||
        form.lastName.trim().length < 2) {
      setError('First and last names must contain at least 2 characters.');
      return;
    }

    if (form.password.length < 8) {
      setError('Password must contain at least 8 characters.');
      return;
    }

    setLoading(true);

    try {
      const result = await registerUser({
        ...form,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        phoneNumber: form.phoneNumber.trim(),
      });

      setMessage(`Account created successfully for ${result.email}. You can now log in.`);
      setForm({
        firstName: '',
        lastName: '',
        email: '',
        phoneNumber: '',
        password: '',
      });
    } catch (requestError) {
      const responseData = requestError.response?.data;
      const serverMessage =
        typeof responseData === 'string'
          ? responseData
          : responseData?.message || responseData?.error;

      setError(
        serverMessage ||
        'Registration failed. Check that the backend is running and try again.'
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <Link className="auth-brand" to="/">ShopSphere</Link>
        <span className="eyebrow">JOIN SHOPSPHERE</span>
        <h1>Create your account</h1>
        <p className="auth-description">
          Set up your customer account to get started.
        </p>

        <div className="auth-name-fields">
          <label>
            First name
            <input
              name="firstName"
              value={form.firstName}
              onChange={handleChange}
              minLength={2}
              maxLength={50}
              autoComplete="given-name"
              required
            />
          </label>

          <label>
            Last name
            <input
              name="lastName"
              value={form.lastName}
              onChange={handleChange}
              minLength={2}
              maxLength={50}
              autoComplete="family-name"
              required
            />
          </label>
        </div>

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
          Phone number
          <input
            name="phoneNumber"
            type="tel"
            value={form.phoneNumber}
            onChange={handleChange}
            minLength={10}
            maxLength={15}
            autoComplete="tel"
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
            minLength={8}
            maxLength={100}
            autoComplete="new-password"
            required
          />
        </label>

        {error && <p className="form-error" role="alert">{error}</p>}
        {message && <p className="form-success" role="status">{message}</p>}

        <button className="auth-submit" type="submit" disabled={loading}>
          {loading ? 'Creating account...' : 'Create account'}
        </button>

        <p className="auth-switch">
          Already registered? <Link to="/login">Log in</Link>
        </p>
      </form>
    </main>
  );
}

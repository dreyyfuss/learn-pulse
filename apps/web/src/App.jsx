import { useEffect, useState } from 'react';
import { RouterProvider } from 'react-router-dom';
import router from './router';
import useAuthStore from './store/authStore';
import { refresh } from './services/authService';

export default function App() {
  const { refreshToken, setToken, setAuth, clearAuth } = useAuthStore();
  const [ready, setReady] = useState(!refreshToken);

  useEffect(() => {
    if (!refreshToken) return;
    refresh(refreshToken)
      .then((res) => {
        const data = res.data;
        if (data.user) {
          const [firstName, ...rest] = (data.user.fullName ?? '').trim().split(' ');
          setAuth({ ...data.user, firstName, lastName: rest.join(' ') }, data.accessToken, data.refreshToken ?? refreshToken);
        } else {
          setToken(data.accessToken);
        }
      })
      .catch(() => clearAuth())
      .finally(() => setReady(true));
  }, []);

  if (!ready) return null;
  return <RouterProvider router={router} />;
}

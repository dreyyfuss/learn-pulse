import api from './api.js';
import useAuthStore from '../store/authStore';

const certificateService = {
  listMine() {
    return api.get('/api/learner/certificates').then(r => r.data);
  },

  async downloadFile(certUuid) {
    const { token } = useAuthStore.getState();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const res = await fetch(`/api/certificates/${certUuid}/download`, { headers });
    if (!res.ok) throw new Error('Download failed');
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `certificate-${certUuid}.pdf`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  },
};

export default certificateService;

import useAuthStore from '../store/authStore';

function authHeaders() {
  const { token } = useAuthStore.getState();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

const certificateService = {
  listMine() {
    return fetch('/api/learner/certificates', { headers: authHeaders() })
      .then(r => r.json())
      .then(r => r.data ?? r);
  },

  // Follows the 302 redirect to the presigned S3 URL, downloads the PDF blob,
  // and triggers a browser save dialog.
  async downloadFile(certUuid) {
    const res = await fetch(`/api/certificates/${certUuid}/download`, {
      headers: authHeaders(),
    });
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

  // Returns the presigned S3 URL (for window.open in the celebration screen).
  async downloadUrl(certUuid) {
    const res = await fetch(`/api/certificates/${certUuid}/download`, {
      headers: authHeaders(),
    });
    if (!res.ok) throw new Error('Download failed');
    // fetch follows the 302 redirect; res.url is the final S3 presigned URL.
    res.body?.cancel();
    return res.url;
  },
};

export default certificateService;
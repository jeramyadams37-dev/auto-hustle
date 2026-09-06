import { useState } from 'react';
import { registerPlugin } from '@capacitor/core';

const Obd2 = registerPlugin('Obd2');
const BACKEND_URL = 'https://auto-hustle-backend-926589006847.us-central1.run.app';

export default function App() {
  const [status, setStatus] = useState('Not connected');
  const [diagnosis, setDiagnosis] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleScan = async () => {
    setLoading(true);
    setDiagnosis(null);
    try {
      setStatus('Connecting...');
      await Obd2.connect();

      setStatus('Reading codes...');
      const codeResult = await Obd2.readCodes();

      if (!codeResult.codes || codeResult.codes.length === 0) {
        setStatus('No trouble codes found. Vehicle looks clean.');
        setLoading(false);
        return;
      }

      const liveResult = await Obd2.readLiveData();

      setStatus('Getting diagnosis...');
      const response = await fetch(`${BACKEND_URL}/diagnose`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          codes: codeResult.codes,
          liveData: liveResult,
          vehicle: {} // TODO: let user enter year/make/model/mileage
        })
      });

      const data = await response.json();
      setDiagnosis(data);
      setStatus('Diagnosis complete');
    } catch (err) {
      setStatus('Error: ' + err.message);
    }
    setLoading(false);
  };

  return (
    <div style={{ padding: 20, fontFamily: 'sans-serif' }}>
      <h1>Auto Hustle</h1>
      <p>{status}</p>
      <button onClick={handleScan} disabled={loading}>
        {loading ? 'Scanning...' : 'Scan Vehicle'}
      </button>

      {diagnosis && (
        <div style={{ marginTop: 20, padding: 15, border: '1px solid #ccc', borderRadius: 8 }}>
          <h3>Urgency: {diagnosis.urgency}</h3>
          <p>{diagnosis.plain_language_summary}</p>
          <h4>Likely causes:</h4>
          <ul>
            {diagnosis.likely_causes?.map((c, i) => <li key={i}>{c}</li>)}
          </ul>
          <p><strong>Estimated cost:</strong> {diagnosis.estimated_cost_range_usd}</p>
          <p><strong>Next step:</strong> {diagnosis.recommended_next_step}</p>
        </div>
      )}
    </div>
  );
}

import React, { useState, useEffect } from 'react';
import { Send, CheckCircle2, AlertCircle, X, Shield, Bell, HelpCircle, Key, UserCheck } from 'lucide-react';

interface TelegramAlertModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const TelegramAlertModal: React.FC<TelegramAlertModalProps> = ({ isOpen, onClose }) => {
  const [botToken, setBotToken] = useState('');
  const [chatId, setChatId] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [status, setStatus] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  const fetchStatus = async () => {
    try {
      const res = await fetch('/api/telegram/status');
      if (res.ok) {
        const data = await res.json();
        setStatus(data);
        if (data.rawChatId) setChatId(data.rawChatId);
        setEnabled(data.enabled !== false);
      }
    } catch (e) {
      console.error('Failed to fetch Telegram status:', e);
    }
  };

  useEffect(() => {
    if (isOpen) {
      fetchStatus();
      setTestResult(null);
      setSaveSuccess(false);
    }
  }, [isOpen]);

  const handleSave = async () => {
    setIsLoading(true);
    setSaveSuccess(false);
    setTestResult(null);
    try {
      const res = await fetch('/api/telegram/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          botToken: botToken.trim(),
          chatId: chatId.trim(),
          enabled
        })
      });
      if (res.ok) {
        const data = await res.json();
        setStatus(data);
        setSaveSuccess(true);
        setTimeout(() => setSaveSuccess(false), 3000);
      } else {
        alert('Failed to save Telegram configuration.');
      }
    } catch (e: any) {
      alert(`Error saving config: ${e.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  const handleTest = async () => {
    setIsLoading(true);
    setTestResult(null);
    try {
      // If user typed in new credentials, save them first (require colon for valid telegram bot tokens)
      const validNewToken = botToken.trim().includes(':') ? botToken.trim() : undefined;
      if (validNewToken || chatId.trim()) {
        await fetch('/api/telegram/config', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            botToken: validNewToken,
            chatId: chatId.trim() || undefined,
            enabled
          })
        });
      }

      const res = await fetch('/api/telegram/test', { method: 'POST' });
      const data = await res.json();
      if (res.ok) {
        setTestResult({ success: true, message: 'Test alert sent! Check your Telegram app now.' });
        fetchStatus();
      } else {
        setTestResult({
          success: false,
          message: data.message || 'Telegram API rejected the request. Please double-check Token & Chat ID.'
        });
      }
    } catch (e: any) {
      setTestResult({ success: false, message: `Connection error: ${e.message}` });
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  const isConfigured = status?.configured || (botToken.trim() && chatId.trim());

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(3, 7, 18, 0.85)',
        backdropFilter: 'blur(8px)',
        zIndex: 9999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px'
      }}
      onClick={onClose}
    >
      <div
        style={{
          background: '#0d131f',
          border: '1px solid rgba(0, 136, 204, 0.4)',
          boxShadow: '0 0 35px rgba(0, 136, 204, 0.25)',
          borderRadius: '12px',
          width: '100%',
          maxWidth: '560px',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column'
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            background: 'linear-gradient(135deg, rgba(0, 136, 204, 0.2), rgba(15, 23, 42, 0.8))',
            borderBottom: '1px solid rgba(0, 136, 204, 0.3)',
            padding: '16px 20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '8px',
                background: 'linear-gradient(135deg, #0088cc, #00c6ff)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 0 12px rgba(0, 136, 204, 0.6)'
              }}
            >
              <Send size={18} color="#fff" style={{ transform: 'translateX(-1px)' }} />
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <h3 style={{ margin: 0, fontSize: '15px', fontWeight: 700, color: '#f3f4f6' }}>
                  Telegram Threat Alert Bot
                </h3>
                {status?.configured ? (
                  <span
                    style={{
                      background: 'rgba(16, 185, 129, 0.2)',
                      color: '#34d399',
                      border: '1px solid rgba(16, 185, 129, 0.4)',
                      fontSize: '10px',
                      padding: '1px 6px',
                      borderRadius: '10px',
                      fontWeight: 700
                    }}
                  >
                    ● CONNECTED
                  </span>
                ) : (
                  <span
                    style={{
                      background: 'rgba(245, 158, 11, 0.2)',
                      color: '#fbbf24',
                      border: '1px solid rgba(245, 158, 11, 0.4)',
                      fontSize: '10px',
                      padding: '1px 6px',
                      borderRadius: '10px',
                      fontWeight: 700
                    }}
                  >
                    NOT CONFIGURED
                  </span>
                )}
              </div>
              <p style={{ margin: 0, fontSize: '11px', color: '#9ca3af' }}>
                Instant phone push notifications for Kali network scans and security incidents
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            style={{
              background: 'transparent',
              border: 'none',
              color: '#9ca3af',
              cursor: 'pointer',
              padding: '4px'
            }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Body */}
        <div style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {/* Quick Guide Card */}
          <div
            style={{
              background: 'rgba(0, 136, 204, 0.08)',
              border: '1px solid rgba(0, 136, 204, 0.25)',
              borderRadius: '8px',
              padding: '12px 14px',
              fontSize: '12px',
              color: '#cbd5e1',
              display: 'flex',
              flexDirection: 'column',
              gap: '6px'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#38bdf8', fontWeight: 600 }}>
              <HelpCircle size={14} />
              How to connect your Telegram Bot (2 Minutes):
            </div>
            <ol style={{ margin: '4px 0 0 16px', padding: 0, lineHeight: 1.5, fontSize: '11px', color: '#94a3b8' }}>
              <li>
                In Telegram, search for <b>@BotFather</b> and send <code>/newbot</code> to get your <b>Bot Token</b>.
              </li>
              <li>
                Open your new bot in Telegram and click <b>START</b> (required so it can message you).
              </li>
              <li>
                Search for <b>@userinfobot</b> on Telegram to see your numerical <b>Chat ID</b> (e.g. <code>123456789</code>).
              </li>
              <li>Paste both values below and click <b>Send Test Alert</b>!</li>
            </ol>
          </div>

          {/* Form Fields */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div>
              <label
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  fontSize: '11px',
                  fontWeight: 600,
                  color: '#94a3b8',
                  marginBottom: '6px'
                }}
              >
                <Key size={13} color="#0088cc" />
                TELEGRAM BOT TOKEN:
              </label>
              <input
                type="password"
                name="telegram_api_bot_token"
                autoComplete="new-password"
                data-lpignore="true"
                data-form-type="other"
                placeholder={status?.botTokenMasked || "e.g. 7123456789:AAF_xxxxxxx_xxxxxxxxxxxxx"}
                value={botToken}
                onChange={(e) => setBotToken(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  background: '#131b2e',
                  border: '1px solid #1e293b',
                  borderRadius: '6px',
                  color: '#f8fafc',
                  fontSize: '12px',
                  fontFamily: 'monospace',
                  outline: 'none',
                  boxSizing: 'border-box'
                }}
              />
              {status?.botTokenMasked && status.botTokenMasked !== "Not Configured" && !botToken && (
                <div style={{ fontSize: '10px', color: '#10b981', marginTop: '3px' }}>
                  ✓ Existing token saved ({status.botTokenMasked}). Leave blank to keep existing.
                </div>
              )}
            </div>

            <div>
              <label
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  fontSize: '11px',
                  fontWeight: 600,
                  color: '#94a3b8',
                  marginBottom: '6px'
                }}
              >
                <UserCheck size={13} color="#0088cc" />
                TELEGRAM CHAT ID:
              </label>
              <input
                type="text"
                placeholder="e.g. 987654321 or your group ID"
                value={chatId}
                onChange={(e) => setChatId(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  background: '#131b2e',
                  border: '1px solid #1e293b',
                  borderRadius: '6px',
                  color: '#f8fafc',
                  fontSize: '12px',
                  fontFamily: 'monospace',
                  outline: 'none',
                  boxSizing: 'border-box'
                }}
              />
            </div>

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                marginTop: '4px',
                padding: '8px 10px',
                background: '#131b2e',
                borderRadius: '6px'
              }}
            >
              <input
                type="checkbox"
                id="tg_enabled"
                checked={enabled}
                onChange={(e) => setEnabled(e.target.checked)}
                style={{ cursor: 'pointer', accentColor: '#0088cc' }}
              />
              <label htmlFor="tg_enabled" style={{ fontSize: '12px', color: '#e2e8f0', cursor: 'pointer' }}>
                Send instant alerts whenever a Kali attack or port probe is intercepted
              </label>
            </div>
          </div>

          {/* Toast / Result Message */}
          {testResult && (
            <div
              style={{
                padding: '10px 14px',
                borderRadius: '6px',
                fontSize: '12px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                background: testResult.success ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
                border: `1px solid ${testResult.success ? 'rgba(16, 185, 129, 0.4)' : 'rgba(239, 68, 68, 0.4)'}`,
                color: testResult.success ? '#34d399' : '#f87171'
              }}
            >
              {testResult.success ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
              {testResult.message}
            </div>
          )}

          {saveSuccess && (
            <div
              style={{
                padding: '10px 14px',
                borderRadius: '6px',
                fontSize: '12px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                background: 'rgba(16, 185, 129, 0.15)',
                border: '1px solid rgba(16, 185, 129, 0.4)',
                color: '#34d399'
              }}
            >
              <CheckCircle2 size={16} />
              Telegram configuration saved successfully!
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div
          style={{
            background: '#090e17',
            borderTop: '1px solid #1e293b',
            padding: '14px 20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
        >
          <button
            onClick={handleTest}
            disabled={isLoading || (!status?.configured && (!botToken.trim() || !chatId.trim()))}
            style={{
              padding: '8px 14px',
              borderRadius: '6px',
              background: 'rgba(0, 136, 204, 0.15)',
              border: '1px solid rgba(0, 136, 204, 0.4)',
              color: '#38bdf8',
              fontSize: '12px',
              fontWeight: 600,
              cursor: isLoading ? 'not-allowed' : 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '6px'
            }}
          >
            <Bell size={13} />
            {isLoading ? 'Sending...' : 'Send Test Alert to Phone'}
          </button>

          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              onClick={onClose}
              style={{
                padding: '8px 14px',
                borderRadius: '6px',
                background: '#1f2937',
                border: '1px solid #374151',
                color: '#9ca3af',
                fontSize: '12px',
                cursor: 'pointer'
              }}
            >
              Close
            </button>
            <button
              onClick={handleSave}
              disabled={isLoading}
              style={{
                padding: '8px 18px',
                borderRadius: '6px',
                background: 'linear-gradient(135deg, #0088cc, #00c6ff)',
                border: 'none',
                color: '#fff',
                fontSize: '12px',
                fontWeight: 700,
                cursor: isLoading ? 'not-allowed' : 'pointer',
                boxShadow: '0 0 12px rgba(0, 136, 204, 0.4)'
              }}
            >
              {isLoading ? 'Saving...' : 'Save Configuration'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

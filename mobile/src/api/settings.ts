import { request } from '../utils/http';

export interface AiUserSettings {
  autoSummaryOnSave: boolean;
  defaultScope: 'workspace' | 'note';
  answerStyle: 'concise' | 'balanced' | 'detailed';
  dailyDigestEnabled: boolean;
  dailyDigestHour: number;
  agentTrustMode: boolean;
  primaryWorkspaceId?: string | null;
}

export function getAiUserSettings() {
  return request<AiUserSettings>({ url: '/settings/ai' });
}

export function updateAiUserSettings(payload: Partial<AiUserSettings>) {
  return request<AiUserSettings>({ url: '/settings/ai', method: 'PATCH', data: payload });
}

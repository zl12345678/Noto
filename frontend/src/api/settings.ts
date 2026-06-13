import http from './http';

export interface AiUserSettings {
  autoSummaryOnSave: boolean;
  defaultScope: 'workspace' | 'note';
  answerStyle: 'concise' | 'balanced' | 'detailed';
  dailyDigestEnabled: boolean;
  dailyDigestHour: number;
  agentTrustMode: boolean;
  primaryWorkspaceId?: string | null;
  primaryWorkspaceName?: string | null;
  focusProjects?: string[];
  agentPreferences?: string | null;
  weeklyRetroEnabled: boolean;
  weeklyRetroHour: number;
  autoExtractTodosOnSave: boolean;
}

export type AiUserSettingsUpdate = Partial<AiUserSettings> & {
  /** 传 0 表示清除常用知识库 */
  primaryWorkspaceId?: string | number | null;
};

export function getAiUserSettings() {
  return http.get<AiUserSettings>('/settings/ai');
}

export function updateAiUserSettings(payload: AiUserSettingsUpdate) {
  return http.patch<AiUserSettings>('/settings/ai', payload);
}

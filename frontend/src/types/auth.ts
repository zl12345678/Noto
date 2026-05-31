export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  user: CurrentUser;
}

export interface CurrentUser {
  id: number;
  username: string;
  nickname: string;
  email: string;
  avatarUrl?: string | null;
}

import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
  timeout: 10000,
});

export const stocksApi = {
  getStocks: () => api.get('/stocks').then((response) => response.data),
  searchStocks: (keyword) =>
    api
      .get('/stocks/search', {
        params: { keyword },
      })
      .then((response) => response.data),
  getStock: (symbol) => api.get(`/stocks/${symbol}`).then((response) => response.data),
  getPrices: (symbol, range) =>
    api
      .get(`/stocks/${symbol}/prices`, {
        params: { range },
      })
      .then((response) => response.data),
  getFinancials: (symbol) => api.get(`/stocks/${symbol}/financials`).then((response) => response.data),
  getMarketEvents: () => api.get('/market-events').then((response) => response.data),
  getStockEvents: (symbol) => api.get(`/stocks/${symbol}/events`).then((response) => response.data),
  runAnalysis: (symbol) => api.post(`/stocks/${symbol}/analysis`).then((response) => response.data),
  getLatestAnalysis: (symbol) => api.get(`/stocks/${symbol}/analysis/latest`).then((response) => response.data),
  runPatternAnalysis: (symbol, { refresh = false } = {}) =>
    api
      .post(`/stocks/${symbol}/chart-pattern-analysis`, null, {
        params: { refresh },
        timeout: 190000,
      })
      .then((response) => response.data),
  getLatestPatternAnalysis: (symbol) =>
    api.get(`/stocks/${symbol}/chart-pattern-analysis/latest`).then((response) => response.data),
};

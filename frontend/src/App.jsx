import { NavLink, Route, Routes } from 'react-router-dom';
import { BarChart3, LineChart, Search } from './icons.js';
import HomePage from './pages/HomePage.jsx';
import StockListPage from './pages/StockListPage.jsx';
import StockDetailPage from './pages/StockDetailPage.jsx';

function App() {
  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink to="/" className="brand" aria-label="주식 인사이트 홈">
          <LineChart size={24} aria-hidden="true" />
          <span>주식 인사이트</span>
        </NavLink>

        <nav className="nav-links" aria-label="주요 메뉴">
          <NavLink to="/" className={({ isActive }) => (isActive ? 'active' : undefined)}>
            <Search size={18} aria-hidden="true" />
            홈
          </NavLink>
          <NavLink to="/stocks" className={({ isActive }) => (isActive ? 'active' : undefined)}>
            <BarChart3 size={18} aria-hidden="true" />
            종목
          </NavLink>
        </nav>
      </header>

      <main className="page-frame">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/stocks" element={<StockListPage />} />
          <Route path="/stocks/:symbol" element={<StockDetailPage />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;

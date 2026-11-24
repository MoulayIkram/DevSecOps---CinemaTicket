import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import reportWebVitals from './reportWebVitals';
import { BrowserRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from './store/configureStore';
import 'react-toastify/dist/ReactToastify.css';
import ScrollToTop from './utils/utilPages/ScrollToTop';
import keycloak, { buildUser } from './keycloak';
import './auth/axiosAuth';
import { addUserToState, removeUserFromState } from './store/actions/userActions';

const root = ReactDOM.createRoot(document.getElementById('root'));
const store = configureStore()
function renderApp() {
  root.render(
    <Provider store={store}>
      <BrowserRouter>
        <ScrollToTop />
        <App />
      </BrowserRouter>
    </Provider>
  );
}

// Rafraîchit le jeton et met à jour Redux quand il expire
keycloak.onTokenExpired = () => {
  keycloak.updateToken(30)
    .then((refreshed) => { if (refreshed) store.dispatch(addUserToState(buildUser(keycloak))); })
    .catch(() => { keycloak.clearToken(); store.dispatch(removeUserFromState()); });
};

// Les pages publiques restent accessibles même si l'utilisateur n'est pas connecté (check-sso)
keycloak.init({
  onLoad: 'check-sso',
  pkceMethod: 'S256',
  checkLoginIframe: false,
  silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
})
  .then((authenticated) => { if (authenticated) store.dispatch(addUserToState(buildUser(keycloak))); })
  .catch((e) => console.error('Keycloak init failed', e))
  .finally(renderApp);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();

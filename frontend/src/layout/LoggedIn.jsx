import React from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { removeUserFromState } from '../store/actions/userActions';
import keycloak from '../keycloak';

export default function LoggedIn() {

  const dispatch = useDispatch();
  const user = useSelector(state => state.user.payload);

  function logout() {
    dispatch(removeUserFromState());
    keycloak.logout({ redirectUri: window.location.origin });
  }

  return (
    <div>
        <ul className="navbar-nav ms-auto py-4 py-lg-0 align-items-center">
            <li class="nav-item">
                <span class="nav-link">
                  Hello, {user?.fullName || user?.email}
                  {user?.roles?.[0] === "ADMIN" ? <span class="badge bg-warning text-dark ms-2">Admin</span> : null}
                </span>
            </li>
            <li class="nav-item">
                <a class="nav-link" href="#!" onClick={logout}>
                  Sign Out
                </a>
            </li>
        </ul>
    </div>
  )
}

/// <reference types="cypress" />

declare global {
  namespace Cypress {
    interface Chainable {
      /** Logs in through the real API and lands on the workspace. */
      login(email: string, password?: string): Chainable<void>
    }
  }
}

Cypress.Commands.add('login', (email: string, password = 'password') => {
  cy.visit('/login')
  cy.get('#email').type(email)
  cy.get('#password').type(password)
  cy.get('button[type=submit]').click()
  cy.get('[data-testid=current-user]').should('exist')
})

export {}

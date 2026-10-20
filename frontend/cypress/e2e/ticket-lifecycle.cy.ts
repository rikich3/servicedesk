/**
 * End-to-end journey against the real backend (demo data seeded by the dev profile):
 * requester creates a ticket → admin assigns it → technician resolves it → requester
 * closes it and reads the full history.
 */
describe('Ticket lifecycle', () => {
  const title = `E2E: VPN drops ${Date.now()}`

  it('walks a ticket from creation to closure across three roles', () => {
    // Requester creates the ticket
    cy.login('user@acme.test')
    cy.contains('a', 'New ticket').click()
    cy.get('#title').type(title)
    cy.get('#description').type('Connection drops every ten minutes on home Wi-Fi.')
    cy.get('#priority').select('HIGH')
    cy.contains('button', 'Create ticket').click()
    cy.get('[data-testid=ticket-title]').should('contain', title)
    cy.contains('No status changes available')
    cy.url().then((url) => {
      const ticketUrl = new URL(url).pathname

      // Admin assigns it to Tom
      cy.contains('button', 'Sign out').click()
      cy.login('admin@acme.test')
      cy.visit(ticketUrl)
      cy.get('select[aria-label=Assignee]').select('Tom Technician')
      cy.get('[data-testid=history]').should('contain', 'Alice Admin assigned to Tom Technician')
      cy.get('[data-testid=ticket-version]').should('have.text', '1')

      // Technician starts, comments and resolves
      cy.contains('button', 'Sign out').click()
      cy.login('tech@acme.test')
      cy.visit(ticketUrl)
      cy.get('[data-testid=transition-IN_PROGRESS]').click()
      cy.get('textarea[aria-label="New comment"]').type('Updated the VPN client, please try again.')
      cy.contains('button', 'Post comment').click()
      cy.get('[data-testid=comments]').should('contain', 'Updated the VPN client')
      cy.get('[data-testid=transition-RESOLVED]').click()
      cy.contains('Resolved').should('exist')

      // Requester confirms and closes
      cy.contains('button', 'Sign out').click()
      cy.login('user@acme.test')
      cy.visit(ticketUrl)
      cy.get('[data-testid=transition-CLOSED]').click()
      cy.get('[data-testid=history]')
        .should('contain', 'Rita Requester created the ticket')
        .and('contain', 'Tom Technician changed status from In progress to Resolved')
        .and('contain', 'Rita Requester changed status from Resolved to Closed')
      cy.contains('No status changes available')
    })
  })

  it('rejects wrong credentials and keeps other organizations invisible', () => {
    cy.visit('/login')
    cy.get('#email').type('user@acme.test')
    cy.get('#password').type('wrong-password')
    cy.get('button[type=submit]').click()
    cy.get('[role=alert]').should('contain', 'Invalid email or password')

    cy.login('admin@globex.test')
    cy.visit('/orgs/1/tickets') // Acme's workspace
    cy.url().should('include', '/orgs')
    cy.url().should('not.include', '/orgs/1/')
  })
})

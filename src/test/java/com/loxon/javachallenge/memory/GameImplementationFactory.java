package com.loxon.javachallenge.memory;

import com.loxon.javachallenge.memory.api.Game;
import com.loxon.javachallenge.memory.api.MemoryState;
import com.loxon.javachallenge.memory.api.Player;
import com.loxon.javachallenge.memory.api.PlayerScore;
import com.loxon.javachallenge.memory.api.communication.commands.*;
import com.loxon.javachallenge.memory.api.communication.general.Command;
import com.loxon.javachallenge.memory.api.communication.general.Response;

import java.util.*;

public class GameImplementationFactory {
    public static Game get() {
        return new Game() {
            private ArrayList<Player> players = new ArrayList<>();
            private List<MemoryState> memory;
            private String[] owners;
            private int rounds;
            private int currentRound = 1;

            private boolean isIndexInvalid(Integer index) {
                return index == null || index < 0 || index >= memory.size();
            }

            private boolean checkAndExecuteMultipleAccess(int cell, ArrayList<Integer> multipleAccesses) {
                if (multipleAccesses.contains(cell)) {
                    if (memory.get(cell) != MemoryState.FORTIFIED) {
                        memory.set(cell, MemoryState.CORRUPT);
                        owners[cell] = null;
                    }
                    return true;
                }
                return false;
            }

            @Override
            public Player registerPlayer(String name) {
                Player player = new Player(name);
                players.add(player);
                return player;
            }

            @Override
            public void startGame(List<MemoryState> initialMemory, int rounds) {
                memory = initialMemory;
                owners = new String[initialMemory.size()];
                this.rounds = rounds;
            }

            @Override
            public List<Response> nextRound(Command... requests) {
                ArrayList<Response> responses = new ArrayList<>();
                ArrayList<String> handledPlayers = new ArrayList<>();
                
                // A többször módosított cellák összegyűjtése
                ArrayList<Integer> modifiedCells = new ArrayList<>();
                ArrayList<Integer> multipleAccesses = new ArrayList<>();
                for (Command command : requests) {
                    List<Integer> cells = new ArrayList<>();
                    if (command instanceof CommandAllocate) {
                        cells = ((CommandAllocate) command).getCells();
                    } else if (command instanceof CommandFree) {
                        cells = ((CommandFree) command).getCells();
                    } else if (command instanceof CommandRecover) {
                        cells = ((CommandRecover) command).getCells();
                    } else if (command instanceof CommandFortify) {
                        cells = ((CommandFortify) command).getCells();
                    } else if (command instanceof CommandSwap) {
                        cells = ((CommandSwap) command).getCells();
                    }
                    
                    for (Integer cell : cells) {
                        if (cell != null) {
                            if (modifiedCells.contains(cell)) {
                                multipleAccesses.add(cell);
                                continue;
                            }
                            modifiedCells.add(cell);
                        }
                    }
                }
                
                // Újrarendezés, hogy a scan commandok a végén legyenek
                ArrayList<Command> commands = new ArrayList<>();
                for (Command command : requests) {
                    if (command instanceof CommandScan) {
                        commands.add(command);
                    } else {
                        commands.add(0, command);
                    }
                }
                
                // Az egy körben megkapott utasítások végrehajtása
                for (Command command : commands) {
                    Player player = command.getPlayer();
                    // Nincs regisztrálva
                    if (!players.contains(player))
                        continue;

                    // Már volt egy kérése
                    if (handledPlayers.contains(player.getName()))
                        continue;
                    handledPlayers.add(player.getName());

                    if (command instanceof CommandStats) {
                        responses.add(executeStats(player));
                    } else if (command instanceof CommandScan) {
                        responses.add(executeScan((CommandScan) command, player));
                    } else if (command instanceof CommandAllocate) {
                        responses.add(executeAllocate((CommandAllocate) command, player, multipleAccesses));
                    } else if (command instanceof CommandFree) {
                        responses.add(executeFree((CommandFree) command, player, multipleAccesses));
                    } else if (command instanceof CommandRecover) {
                        responses.add(executeRecover((CommandRecover) command, player, multipleAccesses));
                    } else if (command instanceof CommandFortify) {
                        responses.add(executeFortify((CommandFortify) command, player, multipleAccesses));
                    } else if (command instanceof CommandSwap) {
                        responses.add(executeSwap((CommandSwap) command, player, multipleAccesses));
                    }
                }
                currentRound++;
                return responses;
            }

            @Override
            public List<PlayerScore> getScores() {
                HashMap<String, PlayerScore> scores = new HashMap<>();
                for (Player player : players) {
                    PlayerScore score = new PlayerScore(player);
                    score.setFortifiedCells(0);
                    score.setOwnedBlocks(0);
                    score.setOwnedCells(0);
                    score.setTotalScore(0);
                    scores.put(player.getName(), score);
                }

                for (int i = 0; i < memory.size() / 4; i++) {
                    String firstOwner = owners[i * 4];
                    boolean ownedByTheSame = firstOwner != null;
                    // Blokkon belűl
                    for (int j = 0; j < 4; j++) {
                        String owner = owners[i * 4 + j];
                        if (firstOwner == null || !firstOwner.equals(owner)) {
                            ownedByTheSame = false;
                        }
                        if (owner != null) {
                            PlayerScore score =  scores.get(owners[i * 4 + j]);
                            score.setOwnedCells(score.getOwnedCells() + 1);
                            switch (memory.get(i * 4 + j)) {
                                case ALLOCATED:
                                    score.setTotalScore(score.getTotalScore() + 1);
                                    break;
                                case FORTIFIED:
                                    score.setTotalScore(score.getTotalScore() + 1);
                                    score.setFortifiedCells(score.getFortifiedCells() + 1);
                                    break;
                            }
                        }
                    }
                    if (ownedByTheSame && firstOwner != null) {
                        PlayerScore score =  scores.get(owners[i * 4]);
                        score.setOwnedBlocks(score.getOwnedBlocks() + 1);
                        score.setTotalScore(score.getTotalScore() + 4);
                    }
                }
                return new ArrayList<>(scores.values());
            }

            @Override
            public String visualize() {
                StringBuilder builder = new StringBuilder("\n\n");
                for (int i = 0; i < memory.size(); i++) {
                    MemoryState state = memory.get(i);
                    builder.append(i);
                    builder.append(": ");
                    builder.append(state.toString());
                    builder.append("(");
                    builder.append(owners[i] == null ? "null" : owners[i]);
                    builder.append("), ");
                    if (i % 8 == 7)
                        builder.append("\n");
                }
                builder.append("\n");
                return builder.toString();
            }
            
            private ResponseStats executeStats(Player player) {
                ResponseStats resp = new ResponseStats(player);
                resp.setCellCount(memory.size());
                int owned = 0;
                int free = 0;
                int allocated = 0;
                int corrupt = 0;
                int fortified = 0;
                int system = 0;
                for (MemoryState state : memory) {
                    switch (state) {
                        case FREE:
                            free++;
                            break;
                        case ALLOCATED:
                            allocated++;
                            break;
                        case CORRUPT:
                            corrupt++;
                            break;
                        case FORTIFIED:
                            fortified++;
                            break;
                        case SYSTEM:
                            system++;
                            break;
                    }
                }
                for (String owner : owners) {
                    if (player.getName().equals(owner))
                        owned++;
                }

                resp.setOwnedCells(owned);
                resp.setFreeCells(free);
                resp.setAllocatedCells(allocated);
                resp.setCorruptCells(corrupt);
                resp.setFortifiedCells(fortified);
                resp.setSystemCells(system);
                resp.setRemainingRounds(rounds - currentRound);
                return resp;
            }
            
            private ResponseScan executeScan(CommandScan command, Player player) {
                int cell = command.getCell();
                if (isIndexInvalid(cell)) {
                    return new ResponseScan(player, -1, Collections.emptyList());
                }

                cell = (cell / 4) * 4;

                ArrayList<MemoryState> states = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    MemoryState state = memory.get(cell + i);
                    if (player.getName().equals(owners[cell + i])) {
                        if (state == MemoryState.ALLOCATED)
                            state = MemoryState.OWNED_ALLOCATED;
                        if (state == MemoryState.FORTIFIED)
                            state = MemoryState.OWNED_FORTIFIED;
                    }
                    states.add(state);
                }
                return new ResponseScan(player, cell, states);
            }
            
            private ResponseSuccessList executeAllocate(CommandAllocate command, Player player, ArrayList<Integer> multipleAccesses) {
                List<Integer> cells = command.getCells();
                ArrayList<Integer> successCells = new ArrayList<>();

                boolean anyInvalid = false;
                if (cells.size() > 2)
                    anyInvalid = true;

                int block = cells.get(0) / 4;
                for (Integer cell : cells) {
                    // Nem ugyanaz a blokk
                    if (isIndexInvalid(cell) || cell / 4 != block) {
                        anyInvalid = true;
                        break;
                    }
                }

                if (anyInvalid)
                    return new ResponseSuccessList(player, successCells);

                for (Integer cell : cells) {
                    if (isIndexInvalid(cell) || checkAndExecuteMultipleAccess(cell, multipleAccesses))
                        continue;
                    
                    MemoryState state = memory.get(cell);
                    if(state == MemoryState.FREE) {
                        memory.set(cell, MemoryState.ALLOCATED);
                        owners[cell] = player.getName();
                        successCells.add(cell);
                    } else if (state == MemoryState.ALLOCATED) {
                        memory.set(cell, MemoryState.CORRUPT);
                        owners[cell] = null;
                    }
                }
                return new ResponseSuccessList(player, successCells);
            }
            
            private ResponseSuccessList executeFree(CommandFree command, Player player, ArrayList<Integer> multipleAccesses) {
                List<Integer> cells = command.getCells();
                ArrayList<Integer> successCells = new ArrayList<>();

                if (cells.size() > 2)
                    return new ResponseSuccessList(player, successCells);

                for (Integer cell : cells) {
                    if (isIndexInvalid(cell) || checkAndExecuteMultipleAccess(cell, multipleAccesses))
                        continue;
                    
                    MemoryState state = memory.get(cell);
                    if (state == MemoryState.ALLOCATED || state == MemoryState.CORRUPT
                            || state == MemoryState.FREE) {
                        memory.set(cell, MemoryState.FREE);
                        owners[cell] = null;
                        successCells.add(cell);
                    }
                }
                return new ResponseSuccessList(player, successCells);
            }
            
            private ResponseSuccessList executeRecover(CommandRecover command, Player player, ArrayList<Integer> multipleAccesses) {
                List<Integer> cells = command.getCells();
                ArrayList<Integer> successCells = new ArrayList<>();

                if (cells.size() > 2)
                    return new ResponseSuccessList(player, successCells);
                
                for (Integer cell : cells) {
                    if (isIndexInvalid(cell) || checkAndExecuteMultipleAccess(cell, multipleAccesses))
                        continue;

                    MemoryState state = memory.get(cell);
                    if (state == MemoryState.CORRUPT) {
                        memory.set(cell, MemoryState.ALLOCATED);
                        owners[cell] = player.getName();
                        successCells.add(cell);
                    } else if (state == MemoryState.ALLOCATED || state == MemoryState.FREE || owners[cell] != null) {
                        memory.set(cell, MemoryState.CORRUPT);
                        owners[cell] = null;
                    }
                }
                return new ResponseSuccessList(player, successCells);
            }
            
            private ResponseSuccessList executeFortify(CommandFortify command, Player player, ArrayList<Integer> multipleAccesses) {
                List<Integer> cells = command.getCells();
                ArrayList<Integer> successCells = new ArrayList<>();
                for (Integer cell : cells) {
                    if (isIndexInvalid(cell) || checkAndExecuteMultipleAccess(cell, multipleAccesses))
                        continue;

                    MemoryState state = memory.get(cell);
                    if (state == MemoryState.ALLOCATED) {
                        memory.set(cell, MemoryState.FORTIFIED);
                        successCells.add(cell);
                    }
                }
                return new ResponseSuccessList(player, successCells);
            }
            
            private ResponseSuccessList executeSwap(CommandSwap command, Player player, ArrayList<Integer> multipleAccesses) {
                List<Integer> cells = command.getCells();
                ArrayList<Integer> successCells = new ArrayList<>();

                if (cells.size() != 2)
                    return new ResponseSuccessList(player, successCells);

                Integer cell1 = cells.get(0);
                Integer cell2 = cells.get(1);

                if (isIndexInvalid(cell1) || isIndexInvalid(cell2))
                    return new ResponseSuccessList(player, successCells);

                if (multipleAccesses.contains(cell1) || multipleAccesses.contains(cell2)) {
                    if (memory.get(cell1) != MemoryState.FORTIFIED)
                        memory.set(cell1, MemoryState.CORRUPT);
                    if (memory.get(cell2) != MemoryState.FORTIFIED)
                        memory.set(cell2, MemoryState.CORRUPT);
                    return new ResponseSuccessList(player, successCells);
                }

                MemoryState state1 = memory.get(cell1);
                MemoryState state2 = memory.get(cell2);
                if (state1 != MemoryState.SYSTEM && state1 != MemoryState.FORTIFIED &&
                        state2 != MemoryState.SYSTEM && state2 != MemoryState.FORTIFIED) {
                    String tempName = owners[cell1];
                    owners[cell1] = owners[cell2];
                    owners[cell2] = tempName;
                    memory.set(cell1, state2);
                    memory.set(cell2, state1);
                    successCells.add(cell1);
                    successCells.add(cell2);
                }
                return new ResponseSuccessList(player, successCells);
            }
            
        };
    }
}